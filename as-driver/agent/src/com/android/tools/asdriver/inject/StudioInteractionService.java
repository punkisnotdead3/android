/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.inject;

import com.android.tools.asdriver.proto.ASDriver;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.labels.LinkLabel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;

/**
 * Service responsible for interacting with the interface.
 */
public class StudioInteractionService {

  /**
   * A prefix for logs so that we can identify them in idea.log if we have to.
   */
  private static final String LOG_PREFIX = "[StudioInteractionService]";

  public StudioInteractionService() { }

  /**
   * Invokes a component. The bulk of the complexity of this method's implementation comes from
   * concurrency and modality. There are three general scenarios that this method covers:
   * <p>
   * <ol>
   *   <li>Successfully finding and invoking a component which spawns a modal dialog</li>
   *   <li>Successfully finding and invoking a component which does not spawn a modal dialog</li>
   *   <li>Unsuccessfully finding or invoking any component</li>
   * </ol>
   * <p>
   * In case #1, if we were to use {@link SwingUtilities#invokeAndWait} to find and invoke the
   * component, then the calling thread would not resume until the modal dialog is closed (due to
   * the "AndWait" part of "invokeAndWait"). In our case, the calling thread is the gRPC server's
   * thread, meaning no future requests from test code could be handled. This effectively means
   * that the test would be forever stalled—no requests can interact with the dialog (so it will
   * never close), and the calling thread is waiting forever in
   * {@link SwingUtilities#invokeAndWait}.
   * <p>
   * In cases #2 and #3, {@link SwingUtilities#invokeAndWait} <i>could</i> be used to find and
   * invoke a component. However, because we have to accommodate case #1 anyway and because we
   * can't distinguish which case we'll be in ahead of time, we need to opt for
   * {@link SwingUtilities#invokeLater}.
   * <p>
   * In all cases, we must ensure that the component does not disappear or otherwise become invalid
   * between <b>finding</b> and <b>invoking</b> (see b/235277847).
   */
  public void invokeComponent(List<ASDriver.ComponentMatcher> matchers) throws InterruptedException, TimeoutException, InvocationTargetException {
    log("Attempting to find and invoke a component with matchers: " + matchers);
    // TODO(b/234067246): consider this timeout when addressing b/234067246.
    int timeoutMillis = 10000;
    long msBetweenRetries = 300;
    long startTime = System.currentTimeMillis();
    long elapsedTime = 0;
    final AtomicBoolean foundComponent = new AtomicBoolean(false);
    final AtomicBoolean invokedComponent = new AtomicBoolean(false);

    while (elapsedTime < timeoutMillis) {
      SwingUtilities.invokeLater(() -> {
          Optional<Component> component = findComponentFromMatchers(matchers);
          if (component.isPresent()) {
            foundComponent.set(true);
            invokeComponentInternal(component.get());
          }
      });

      // The invokeLater call above queues a Runnable to be executed on the UI thread at some point
      // in the future. This means that the calling thread continues its own execution immediately.
      // However, the calling thread needs to know whether the Runnable was successful so that we
      // can decide whether to return, retry, or timeout.
      //
      // In cases #2 and #3 from the method-level comment, the invokeAndWait Runnable below will
      // only run once the invokeLater Runnable is complete.
      //
      // In case #1 though, the invokeLater Runnable will eventually try spawning a modal dialog,
      // at which point Swing will know that it can execute the invokeAndWait Runnable even though
      // the invokeLater Runnable hasn't finished.
      SwingUtilities.invokeAndWait(() -> {
        if (foundComponent.get()) {
          // Note: all we can know is that we ATTEMPTED to invoke the component, not that it was
          // successful.
          invokedComponent.set(true);
        }
      });

      if (invokedComponent.get()) {
        break;
      }
      Thread.sleep(msBetweenRetries);
      elapsedTime = System.currentTimeMillis() - startTime;
    }

    if (elapsedTime >= timeoutMillis) {
      throw new TimeoutException(String.format("Timed out after %dms", elapsedTime));
    }
  }

  private void log(String text) {
    System.out.printf("%s %s%n", LOG_PREFIX, text);
  }

  private void invokeComponentInternal(Component component) {
    if (component instanceof ActionLink) {
      ActionLink componentAsLink = (ActionLink)component;
      log("Invoking ActionLink: " + componentAsLink);
      performAction(componentAsLink.getAction(), componentAsLink);
    } else if (component instanceof LinkLabel) {
      LinkLabel componentAsLink = (LinkLabel)component;
      log("Invoking LinkLabel: " + componentAsLink);
      componentAsLink.doClick();
    } else if (component instanceof NotificationComponent) {
      log("Invoking hyperlink in Notification: " + component);
      ((NotificationComponent)component).hyperlinkUpdate();
    } else if (component instanceof JButton) {
      log("Invoking JButton: " + component);
      invokeButton((JButton)component);
    } else {
      throw new IllegalArgumentException(String.format("Don't know how to invoke a component of class \"%s\"", component.getClass()));
    }
  }

  /**
   * Finds a component (if exactly one exists) based on a list of matchers.
   *
   * This method abstracts the complexity of the platform so that callers have an easy-to-use API.
   */
  private Optional<Component> findComponentFromMatchers(List<ASDriver.ComponentMatcher> matchers) {
    Set<Component> componentsFound = getEntireSwingHierarchy();

    for (ASDriver.ComponentMatcher matcher : matchers) {
      if (matcher.hasComponentTextMatch()) {
        ASDriver.ComponentTextMatch match = matcher.getComponentTextMatch();
        String text = match.getText();
        componentsFound = findComponentsMatchingText(componentsFound, text);
      } else if (matcher.hasSvgIconMatch()) {
        ASDriver.SvgIconMatch match = matcher.getSvgIconMatch();
        componentsFound = findLinksByIconNames(componentsFound, match.getIconList());
      } else if (matcher.hasSwingClassRegexMatch()) {
        ASDriver.SwingClassRegexMatch match = matcher.getSwingClassRegexMatch();
        String regex = match.getRegex();
        componentsFound = findComponentsMatchingRegex(componentsFound, regex);
      } else {
        throw new IllegalArgumentException("ComponentMatcher doesn't have a recognized matcher");
      }
    }

    int numComponentsFound = componentsFound.size();
    if (numComponentsFound > 1) {
      throw new IllegalStateException(String.format("Found %s component(s) but expected exactly one. Please construct more specific match criteria.",
                                                    numComponentsFound));
    }

    return componentsFound.stream().findFirst();
  }

  /**
   * Finds all components whose class names match the given regex and returns their entire Swing
   * subtrees (including the matching components themselves).
   */
  private Set<Component> findComponentsMatchingRegex(Set<Component> componentsToLookUnder, String regex) {
    Predicate<? super Component> classMatchesRegex = (c) -> c.getClass().toString().matches(regex);
    Set<Component> componentsFound = componentsToLookUnder.stream().filter(classMatchesRegex).collect(Collectors.toSet());

    List<Component> componentsUnderFoundComponents = new ArrayList<>();
    for (Component component : componentsFound) {
      componentsUnderFoundComponents.addAll(getAllComponentsUnder(component));
    }
    componentsFound.addAll(componentsUnderFoundComponents);

    return componentsFound;
  }

  private Set<Component> findComponentsMatchingText(Set<Component> componentsToLookUnder, String text) {
    Predicate<? super Component> filterByText = (c) -> {
      String componentText = getTextFromComponent(c);

      // Remove any escape characters introduced by mnemonics from the component's text.
      String textWithoutEscapeCharacter = componentText == null ? null : componentText.replaceAll("[\\x1B]", "");
      return Objects.equals(componentText, text) || Objects.equals(textWithoutEscapeCharacter, text);
    };
    Set<Component> componentsFound = componentsToLookUnder.stream().filter(filterByText).collect(Collectors.toSet());

    // Notifications are searched separately because the text is embedded in an inaccessible way.
    componentsFound.addAll(findNotificationByDisplayId(text));

    return componentsFound;
  }

  private Collection<? extends Component> findNotificationByDisplayId(String displayId) {
    Notification[] allNotifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification.class, null);

    return Arrays.stream(allNotifications)
      .filter((n) -> Objects.equals(n.getDisplayId(), displayId))
      .map(NotificationComponent::new)
      .collect(Collectors.toList());
  }

  /**
   * Gets an icon's underlying icon name(s) ({@link LayeredIcon} instances can have multiple).
   */
  private List<String> getIconNamesFromIcon(Icon icon) {
    List<String> paths = new ArrayList<>();
    if (icon instanceof IconLoader.CachedImageIcon) {
      String path = ((IconLoader.CachedImageIcon)icon).getOriginalPath();
      paths.add(path);
    }
    else if (icon instanceof LayeredIcon) {
      LayeredIcon layeredIcon = (LayeredIcon)icon;
      for (int i = 0; i < layeredIcon.getIconCount(); i++) {
        Icon subIcon = layeredIcon.getIcon(i);
        List<String> subPaths = getIconNamesFromIcon(subIcon);
        paths.addAll(subPaths);
      }
    }

    return paths;
  }

  private Set<Component> findLinksByIconNames(Collection<Component> components, List<String> iconsToMatchAgainst) {
    Set<Component> matchingLinks = new HashSet<>();
    for (Component c : components) {
      if (!(c instanceof ActionLink)) {
        continue;
      }
      ActionLink link = (ActionLink)c;
      List<String> iconNames = getIconNamesFromIcon(link.getIcon());
      for (String iconName : iconNames) {
        if (iconsToMatchAgainst.contains(iconName)) {
          matchingLinks.add(c);
        }
      }
    }

    return matchingLinks.stream().collect(Collectors.toSet());
  }

  private Set<Component> getEntireSwingHierarchy() {
    Set<Component> allComponents = new HashSet<>();
    for (Window window : Frame.getWindows()) {
      List<Component> componentsInWindow = getAllComponentsUnder(window);
      allComponents.addAll(componentsInWindow);
      allComponents.add(window);
    }

    return allComponents;
  }

  private void invokeButton(JButton button) {
    Action action = button.getAction();
    ActionEvent ae = new ActionEvent(button, 0, null);
    action.actionPerformed(ae);
  }

  /**
   * Invoke a particular component's action.
   *
   * @param c If specified, this will be used as the context for the action. If unspecified, the
   *          most recently focused window will be used instead.
   */
  private void performAction(AnAction action, Component c) {
    if (c == null) {
      c = ApplicationManager.getApplication().getService(IdeFocusManager.class).getLastFocusedIdeWindow();
    }

    DataContext context = DataManager.getInstance().getDataContext(c);
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context);
    action.actionPerformed(event);
  }

  /**
   * Returns the text associated with the given component. Not all components have text, and not
   * all components that <i>do</i> have text are accounted for by this function (in which case,
   * feel free to modify the implementation to accommodate your needs).
   */
  private String getTextFromComponent(Component c) {
    if (c instanceof JLabel) {
      return ((JLabel)c).getText();
    }
    if (c instanceof AbstractButton) {
      return ((AbstractButton)c).getText();
    }

    return null;
  }

  /**
   * Fetches all components under and including the {@code root} recursively.
   */
  private List<Component> getAllComponentsUnder(Component root) {
    List<Component> componentsFound = new ArrayList<>();
    Queue<Component> componentsToSearch = new LinkedList<>();
    componentsToSearch.add(root);
    while (!componentsToSearch.isEmpty()) {
      Component c = componentsToSearch.poll();
      if (c instanceof Container) {
        Container container = (Container)c;
        Collections.addAll(componentsToSearch, container.getComponents());
      }
      componentsFound.add(c);
    }
    return componentsFound;
  }

  /**
   * Wraps {@link Notification} in a {@link Component} so that it can be treated like other
   * {@link Component} instances for the sake of invoking them.
   */
  private static class NotificationComponent extends Component {
    private final Notification notification;

    public NotificationComponent(Notification notification) {
      this.notification = notification;
    }

    public void hyperlinkUpdate() {
      try {
        String source = "Link inside notification";
        HyperlinkEvent e = new HyperlinkEvent(source, HyperlinkEvent.EventType.ACTIVATED, new URL("http://localhost/madeup"));
        notification.getListener().hyperlinkUpdate(notification, e);
      }
      catch (MalformedURLException ex) {
        ex.printStackTrace();
      }
    }
  }
}
