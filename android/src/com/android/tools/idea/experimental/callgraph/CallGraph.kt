/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.experimental.callgraph

import com.android.tools.idea.experimental.callgraph.CallGraph.Edge
import com.android.tools.idea.experimental.callgraph.CallGraph.Node
import com.android.tools.idea.experimental.callgraph.CallTarget.*
import com.google.common.collect.Lists
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.uast.*
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.collections.LinkedHashSet

private val LOG = Logger.getInstance(CallGraph::class.java)

sealed class CallTarget(open val element: UElement) {
  data class Method(override val element: UMethod) : CallTarget(element)
  data class Lambda(override val element: ULambdaExpression) : CallTarget(element)
  data class DefaultConstructor(override val element: UClass) : CallTarget(element)
}

/** A graph in which nodes represent methods (including lambdas) and edges indicate that one method calls another. */
interface CallGraph {
  val nodes: Collection<Node>

  interface Node {
    val caller: CallTarget
    val edges: Collection<Edge>
    val likelyEdges: Collection<Edge> get() = edges.filter { it.isLikely }

    fun findEdge(other: Node) = edges.find { it.node == other }

    /** Describes this node in a form like class#method, using "anon" for anonymous classes and lambdas. */
    val shortName: String get() {
      val containingClass = caller.element.getContainingUClass()?.name ?: "anon"
      val containingMethod = caller.element.getContainingUMethod()?.name ?: "anon"
      val caller = caller // Enables smart casts.
      return when (caller) {
        is Method -> "${containingClass}#${caller.element.name}"
        is Lambda -> "${containingClass}#${containingMethod}#lambda"
        is DefaultConstructor -> "${caller.element.name}#defaultCtor"
      }
    }
  }

  /** An edge to [node] of type [kind] due to [call]. Note that [call] can be null for, e.g., implicit calls to super constructors. */
  data class Edge(val node: Node, val call: UCallExpression?, val kind: Kind) {
    val isLikely: Boolean get() = kind.isLikely

    enum class Kind {
      DIRECT, // A statically dispatched call.
      UNIQUE, // A call that appears to have a single implementation.
      TYPE_EVIDENCED, // A call evidenced by runtime type estimates.
      BASE, // The base method of a call (always added).
      NON_UNIQUE_OVERRIDE; // A call that is one of several overriding implementations.

      val isLikely: Boolean get() = when (this) {
        DIRECT, UNIQUE, TYPE_EVIDENCED -> true
        BASE, NON_UNIQUE_OVERRIDE -> false
      }
    }
  }

  fun getNode(element: UElement): Node

  /** Describes all contents of the call graph (for debugging). */
  @Suppress("UNUSED")
  fun dump(filter: (Edge) -> Boolean = { true }) = buildString {
    for (node in nodes.sortedBy { it.shortName }) {
      val callees = node.edges.filter(filter)
      if (callees.isNotEmpty()) {
        appendln(node.shortName)
        callees.forEach { appendln("    ${it.node.shortName} [${it.kind}]") }
      }
    }
  }

  @Suppress("UNUSED")
  fun outputToDotFile(file: String, filter: (Edge) -> Boolean = { true }) {
    PrintWriter(BufferedWriter(FileWriter(file))).use { writer ->
      writer.println("digraph {")
      for (node in nodes) {
        for ((callee) in node.likelyEdges) {
          writer.print("  \"${node.shortName}\" -> \"${callee.shortName}\"")
        }
      }
      writer.println("}")
    }
  }
}

class MutableCallGraph : CallGraph {
  private val nodeMap = LinkedHashMap<UElement, MutableNode>()
  override val nodes get() = nodeMap.values

  class MutableNode(override val caller: CallTarget,
                    override val edges: MutableCollection<Edge> = ArrayList()) : Node

  override fun getNode(element: UElement): MutableNode {
    return nodeMap.getOrPut(element) {
      val caller = when (element) {
        is UMethod -> Method(element)
        is ULambdaExpression -> Lambda(element)
        is UClass -> DefaultConstructor(element)
        else -> throw Error("Unexpected UElement type ${element.javaClass}")
      }
      MutableNode(caller)
    }
  }

  override fun toString(): String {
    val numEdges = nodes.asSequence().map { it.edges.size }.sum()
    return "Call graph: ${nodeMap.size} nodes, ${numEdges} edges"
  }
}

/** Returns non-intersecting paths from nodes in [sources] to nodes for which [isSink] returns true. */
fun <T : Any> searchForPaths(sources: Collection<T>,
                             isSink: (T) -> Boolean,
                             getNeighbors: (T) -> Collection<T>): Collection<List<T>> {
  val res = ArrayList<List<T>>()
  val prev = LinkedHashMap<T, T?>(sources.associate { Pair(it, null) })
  fun T.seen() = this in prev
  val used = LinkedHashSet<T>() // Nodes already part of a result path.
  val q = ArrayDeque<T>(sources.toSet())
  while (!q.isEmpty()) {
    val n = q.removeFirst()
    if (isSink(n)) {
      // Keep running time linear by preempting path construction if it intersects with one already seen.
      val path = generateSequence(n) { if (it in used) null else prev[it] }.toList().reversed()
      if (path.none { it in used })
        res.add(path)
      used.addAll(path)
    }
    else {
      getNeighbors(n).filter { !it.seen() }.forEach {
        q.addLast(it)
        prev[it] = n
      }
    }
  }
  LOG.info("Number of nodes in path search: ${prev.size}")
  return res
}

/**
 * A context-sensitive search for paths through the call graph.
 * See "The Cartesian Product Algorithm" by Ole Agesen.
 */
fun CallGraph.searchForPaths(sources: Collection<Node>, sinks: Collection<Node>,
                             nonContextualReceiverEval: CallReceiverEvaluator): Collection<List<Node>> {

  /** Describes a parameter specialization tuple, mapping each parameter to a single concrete receiver. */
  data class ParamContext(val params: List<Pair<UParameter, Receiver>>, val implicitThis: Receiver?) {
    operator fun get(param: UParameter) = params.firstOrNull { it.first == param }?.second
  }

  val emptyParamContext = ParamContext(emptyList(), /*implicitThis*/ null)

  /** Augments the non-contextual receiver evaluator with a parameter context. */
  class ContextualReceiverEvaluator(val paramContext: ParamContext) : CallReceiverEvaluator {

    override fun get(element: UElement): Collection<Receiver> = when (element) {
      is UThisExpression -> getForImplicitThis() // TODO: Qualified `this` not yet supported in UAST.
      is UParameter -> nonContextualReceiverEval[element] + listOfNotNull(paramContext[element])
      is USimpleNameReferenceExpression -> element.resolveToUElement()?.let { this[it] } ?: emptyList() // Recurse when resolved.
      else -> nonContextualReceiverEval[element]
    }

    override fun getForImplicitThis(): Collection<Receiver> = listOfNotNull(paramContext.implicitThis)
  }

  /**
   * A specialization of a call graph node on a parameter context.
   * We use pointer equality for [node] and structural equality for [paramContext].
   */
  data class SearchNode(val node: Node, val paramContext: ParamContext)

  /**
   * Builds parameter contexts for the target of [call] by taking the Cartesian product of the call argument receivers.
   * Returns an empty parameter context if there are no call argument receivers.
   */
  fun CallTarget.buildParamContextsFromCall(call: UCallExpression, receiverEval: CallReceiverEvaluator): Collection<ParamContext> {
    val params = when (this) {
      is Method -> element.uastParameters
      is Lambda -> element.valueParameters
      is DefaultConstructor -> emptyList()
    }
    val args = call.valueArguments
    if (args.size != params.size) {
      LOG.warn("Number of arguments does not match number of parameters")
      return emptyList()
    }
    val argReceivers = args.map { receiverEval[it].toList() }

    // We will take a Cartesian product, so filter out empty receiver sets.
    val (paramsWithReceivers, nonEmptyArgReceivers) = params.zip(argReceivers)
        .filter { it.second.isNotEmpty() }
        .unzip()

    // The potential for an implicit receiver argument to the callee complicates the logic here.
    // When creating the Cartesian product we include the implicit receiver argument as a "normal" call argument, then
    // pull it out again when creating parameter contexts.

    val callHasReceiver = when (this) {
      is Method -> !element.isStatic
      is Lambda -> false // TODO: Kotlin lambdas can have receivers, but this seems to not be reflected in UAST (yet?).
      is DefaultConstructor -> false // Constructors technically take in a pointer to `this`, but it doesn't help this analysis.
    }

    val callReceiver = call.receiver
    val implicitReceiverReceivers = when {
      callHasReceiver && callReceiver != null -> receiverEval[callReceiver].toList()
      callHasReceiver && callReceiver == null -> receiverEval.getForImplicitThis().toList()
      else -> emptyList()
    }

    val hasImplicitReceivers = implicitReceiverReceivers.isNotEmpty()
    val allReceiverLists =
        if (hasImplicitReceivers) listOf(implicitReceiverReceivers) + nonEmptyArgReceivers
        else nonEmptyArgReceivers
    val numImplicitArgs = if (hasImplicitReceivers) 1 else 0

    // Zip formal parameters with all possible argument receiver combinations.
    val cartesianProd = Lists.cartesianProduct(allReceiverLists)
    val maxNumParamContexts = 1000
    if (cartesianProd.size > maxNumParamContexts)
      LOG.warn("Combinatorial explosion for call: ${call.methodName}")
    val paramContexts = cartesianProd
        .take(maxNumParamContexts)
        .map { receiverTuple ->
          ParamContext(paramsWithReceivers.zip(receiverTuple.drop(numImplicitArgs)),
                       receiverTuple.take(numImplicitArgs).firstOrNull())
        }
    assert(paramContexts.isNotEmpty())
    return paramContexts
  }

  /** Examines call sites to find contextualized neighbors of a search node. */
  fun getNeighbors(searchNode: SearchNode): Collection<SearchNode> {
    return searchNode.node.edges.flatMap { edge ->
      val contextualReceiverEval = ContextualReceiverEvaluator(searchNode.paramContext)
      when {
        edge.isLikely && edge.call != null -> {
          val paramContexts = edge.node.caller.buildParamContextsFromCall(edge.call, contextualReceiverEval)
          paramContexts.map { SearchNode(edge.node, it) }
        }
        edge.kind == Edge.Kind.BASE && edge.call != null -> {
          // Try to refine the base method to a concrete target.
          edge.call.getTargets(contextualReceiverEval).flatMap { target ->
            val paramContexts = target.buildParamContextsFromCall(edge.call, contextualReceiverEval)
            paramContexts.map { SearchNode(getNode(target.element), it) }
          }
        }
        else -> emptyList()
      }
    }
  }

  // We need to initialize the search with all possible parameter contexts for the source nodes.
  // To do this we employ a nice trick: we search for paths from *all* nodes in the call graph (specialized
  // on empty parameter contexts) and take note of all parameter contexts found for the source nodes.
  val sourceSet = sources.toSet()
  val searchSources = ArrayList<SearchNode>()
  val contextInitSources = nodes.map { SearchNode(it, emptyParamContext) }
  searchForPaths(contextInitSources, { if (it.node in sourceSet) searchSources.add(it); false }, ::getNeighbors)

  val sinkSet = sinks.toSet()
  return searchForPaths(searchSources, { it.node in sinkSet }, ::getNeighbors)
      .map { path -> path.map { it.node } } // Strip away parameter contexts.
}