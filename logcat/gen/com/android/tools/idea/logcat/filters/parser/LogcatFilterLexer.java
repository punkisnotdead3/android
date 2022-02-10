/* The following code was generated by JFlex 1.7.0 tweaked for IntelliJ platform */

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

/*
 * Defines tokens in the Logcat Filter Query Language. The language is based on the Buganizer query language specific fields can be queried
 * independently but also, a general query. For example:
 *
 *    foo bar tag: MyTag package: com.example.app
 *
 * Matches log lines that
 *
 *   TAG.contains("MyTag") && PACKAGE.contains("com.example.app") && line.contains("foo bar")
 *
 * Definitions:
 *   term: A top level entity which can either be a string value or a key-value pair
 *   key-term: A key-value term. Matches a field named by the key with the value.
 *   value-term: A top level entity representing a string. Matches the entire log line with the value.
 *
 * There are 2 types of keys. String keys can accept quoted or unquoted values while regular keys can only take an unquoted value with no
 * whitespace. String keys can also be negated and can specify a regex match:
 * String keys examples:
 *     tag: foo
 *     tag: fo\ o
 *     tag: 'foo'
 *     tag: 'fo\'o'
 *     tag: "foo"
 *     tag: "fo\"o"
 *     -tag: foo
 *     tag~: foo|bar
 *
 * Logical operations & (and), | (or) are supported as well as parenthesis.
 *
 * Implicit grouping:
 * Terms without logical operations between them are treated as an implicit AND unless they are value terms:
 *
 *   foo bar tag: MyTag -> line.contains("foo bar") && tag.contains("MyTag")
 *
 * This file is used by Grammar-Kit to generate the lexer, parser, node types and PSI classes.
 */
package com.android.tools.idea.logcat.filters.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.android.tools.idea.logcat.filters.parser.*;
import com.intellij.psi.TokenType;


/**
 * This class is a scanner generated by 
 * <a href="http://www.jflex.de/">JFlex</a> 1.7.0
 * from the specification file <tt>LogcatFilter.flex</tt>
 */
class LogcatFilterLexer implements FlexLexer {

  /** This character denotes the end of file */
  public static final int YYEOF = -1;

  /** initial size of the lookahead buffer */
  private static final int ZZ_BUFFERSIZE = 16384;

  /** lexical states */
  public static final int YYINITIAL = 0;
  public static final int STRING_KVALUE_STATE = 2;
  public static final int REGEX_KVALUE_STATE = 4;
  public static final int KVALUE_STATE = 6;

  /**
   * ZZ_LEXSTATE[l] is the state in the DFA for the lexical state l
   * ZZ_LEXSTATE[l+1] is the state in the DFA for the lexical state l
   *                  at the beginning of a line
   * l is of the form l = 2*k, k a non negative integer
   */
  private static final int ZZ_LEXSTATE[] = { 
     0,  0,  1,  1,  2,  2,  3, 3
  };

  /** 
   * Translates characters to character classes
   * Chosen bits are [7, 7, 7]
   * Total runtime size is 1928 bytes
   */
  public static int ZZ_CMAP(int ch) {
    return ZZ_CMAP_A[(ZZ_CMAP_Y[ZZ_CMAP_Z[ch>>14]|((ch>>7)&0x7f)]<<7)|(ch&0x7f)];
  }

  /* The ZZ_CMAP_Z table has 68 entries */
  static final char ZZ_CMAP_Z[] = zzUnpackCMap(
    "\1\0\103\200");

  /* The ZZ_CMAP_Y table has 256 entries */
  static final char ZZ_CMAP_Y[] = zzUnpackCMap(
    "\1\0\1\1\53\2\1\3\22\2\1\4\37\2\1\3\237\2");

  /* The ZZ_CMAP_A table has 640 entries */
  static final char ZZ_CMAP_A[] = zzUnpackCMap(
    "\11\0\5\1\22\0\1\13\1\0\1\11\3\0\1\6\1\14\1\7\1\10\3\0\1\3\14\0\1\2\41\0\1"+
    "\12\4\0\1\23\1\0\1\26\1\0\1\20\1\0\1\24\1\0\1\16\1\0\1\27\1\15\1\21\1\17\1"+
    "\0\1\25\2\0\1\22\1\30\1\0\1\31\5\0\1\5\1\0\1\4\6\0\1\1\32\0\1\1\337\0\1\1"+
    "\177\0\13\1\35\0\2\1\5\0\1\1\57\0\1\1\40\0");

  /** 
   * Translates DFA states to action switch labels.
   */
  private static final int [] ZZ_ACTION = zzUnpackAction();

  private static final String ZZ_ACTION_PACKED_0 =
    "\4\0\1\1\1\2\1\1\1\3\1\4\1\5\1\6"+
    "\2\7\5\1\1\10\3\7\1\11\2\7\1\12\2\1"+
    "\1\0\1\1\3\0\6\1\1\10\1\0\1\10\3\0"+
    "\1\11\1\0\1\11\3\0\10\1\2\10\2\11\2\1"+
    "\1\13\1\14\2\1\1\15";

  private static int [] zzUnpackAction() {
    int [] result = new int[70];
    int offset = 0;
    offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAction(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /** 
   * Translates a state to a row index in the transition table
   */
  private static final int [] ZZ_ROWMAP = zzUnpackRowMap();

  private static final String ZZ_ROWMAP_PACKED_0 =
    "\0\0\0\32\0\64\0\116\0\150\0\202\0\234\0\150"+
    "\0\150\0\266\0\266\0\320\0\352\0\u0104\0\u011e\0\u0138"+
    "\0\u0152\0\u016c\0\u0186\0\266\0\u01a0\0\u01ba\0\u01d4\0\u01ee"+
    "\0\u0208\0\u0222\0\u023c\0\u0256\0\320\0\266\0\u0270\0\352"+
    "\0\u028a\0\u02a4\0\u02be\0\u02d8\0\u02f2\0\u030c\0\u0326\0\u0340"+
    "\0\u01a0\0\266\0\u035a\0\u01ba\0\u0374\0\u038e\0\u01ee\0\266"+
    "\0\u03a8\0\u0208\0\u03c2\0\320\0\352\0\u03dc\0\u03f6\0\u0410"+
    "\0\u042a\0\u0444\0\u045e\0\u01a0\0\u01ba\0\u01ee\0\u0208\0\u0478"+
    "\0\u0492\0\150\0\150\0\u04ac\0\u04c6\0\150";

  private static int [] zzUnpackRowMap() {
    int [] result = new int[70];
    int offset = 0;
    offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackRowMap(String packed, int offset, int [] result) {
    int i = 0;  /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int high = packed.charAt(i++) << 16;
      result[j++] = high | packed.charAt(i++);
    }
    return j;
  }

  /** 
   * The transition table of the DFA
   */
  private static final int [] ZZ_TRANS = zzUnpackTrans();

  private static final String ZZ_TRANS_PACKED_0 =
    "\1\5\1\6\1\5\1\7\1\5\1\10\1\11\1\12"+
    "\1\13\1\14\1\5\1\6\1\15\1\16\3\5\1\17"+
    "\1\5\1\20\1\5\1\21\2\5\1\22\1\5\1\23"+
    "\1\6\5\23\2\24\1\25\1\23\1\6\1\26\15\23"+
    "\1\27\1\6\5\27\2\24\1\30\1\27\1\6\1\31"+
    "\15\27\1\32\1\6\11\32\1\6\16\32\1\5\1\0"+
    "\5\5\2\0\1\5\1\33\1\0\16\5\1\0\1\6"+
    "\11\0\1\6\16\0\1\5\1\0\5\5\2\0\1\5"+
    "\1\33\1\0\1\5\1\34\3\5\1\17\3\5\1\21"+
    "\2\5\1\22\1\5\32\0\11\35\1\36\1\37\17\35"+
    "\12\40\1\41\1\40\1\36\15\40\1\5\1\0\5\5"+
    "\2\0\1\5\1\33\1\0\2\5\1\42\1\5\1\43"+
    "\12\5\1\0\5\5\2\0\1\5\1\33\1\0\4\5"+
    "\1\44\12\5\1\0\5\5\2\0\1\5\1\33\1\0"+
    "\10\5\1\45\6\5\1\0\5\5\2\0\1\5\1\33"+
    "\1\0\7\5\1\46\7\5\1\0\5\5\2\0\1\5"+
    "\1\33\1\0\7\5\1\47\6\5\1\23\1\0\5\23"+
    "\2\0\1\23\1\50\1\0\16\23\11\51\1\52\1\53"+
    "\17\51\12\54\1\55\1\54\1\52\15\54\1\27\1\0"+
    "\5\27\2\0\1\27\1\56\1\0\16\27\11\57\1\60"+
    "\1\61\17\57\12\62\1\63\1\62\1\60\15\62\1\32"+
    "\1\0\11\32\1\0\16\32\1\5\1\0\5\5\2\0"+
    "\1\5\1\33\20\5\1\0\5\5\2\0\1\5\1\33"+
    "\1\0\2\5\1\42\13\5\11\35\1\64\1\37\17\35"+
    "\12\40\1\41\1\40\1\65\15\40\1\5\1\0\5\5"+
    "\2\0\1\5\1\33\1\0\3\5\1\66\13\5\1\0"+
    "\5\5\2\0\1\5\1\33\1\0\15\5\1\67\1\5"+
    "\1\0\5\5\2\0\1\5\1\33\1\0\6\5\1\70"+
    "\10\5\1\0\5\5\2\0\1\5\1\33\1\0\4\5"+
    "\1\71\12\5\1\0\5\5\2\0\1\5\1\33\1\0"+
    "\12\5\1\72\4\5\1\0\5\5\2\0\1\5\1\33"+
    "\1\0\10\5\1\73\5\5\1\23\1\0\5\23\2\0"+
    "\1\23\1\50\17\23\11\51\1\74\1\53\17\51\12\54"+
    "\1\55\1\54\1\75\15\54\1\27\1\0\5\27\2\0"+
    "\1\27\1\56\17\27\11\57\1\76\1\61\17\57\12\62"+
    "\1\63\1\62\1\77\15\62\1\5\1\0\5\5\2\0"+
    "\1\5\1\33\1\0\4\5\1\73\12\5\1\0\5\5"+
    "\2\0\1\5\1\33\1\0\4\5\1\100\12\5\1\0"+
    "\5\5\2\0\1\5\1\33\1\0\6\5\1\101\10\5"+
    "\1\0\1\102\4\5\2\0\1\5\1\33\1\0\17\5"+
    "\1\0\5\5\2\0\1\5\1\33\1\0\13\5\1\101"+
    "\3\5\1\0\1\103\1\5\1\104\2\5\2\0\1\5"+
    "\1\33\1\0\17\5\1\0\5\5\2\0\1\5\1\33"+
    "\1\0\1\5\1\71\15\5\1\0\5\5\2\0\1\5"+
    "\1\33\1\0\7\5\1\105\7\5\1\0\1\106\4\5"+
    "\2\0\1\5\1\33\1\0\17\5\1\0\5\5\2\0"+
    "\1\5\1\33\1\0\10\5\1\66\5\5";

  private static int [] zzUnpackTrans() {
    int [] result = new int[1248];
    int offset = 0;
    offset = zzUnpackTrans(ZZ_TRANS_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackTrans(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      value--;
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /* error codes */
  private static final int ZZ_UNKNOWN_ERROR = 0;
  private static final int ZZ_NO_MATCH = 1;
  private static final int ZZ_PUSHBACK_2BIG = 2;

  /* error messages for the codes above */
  private static final String[] ZZ_ERROR_MSG = {
    "Unknown internal scanner error",
    "Error: could not match input",
    "Error: pushback value was too large"
  };

  /**
   * ZZ_ATTRIBUTE[aState] contains the attributes of state <code>aState</code>
   */
  private static final int [] ZZ_ATTRIBUTE = zzUnpackAttribute();

  private static final String ZZ_ATTRIBUTE_PACKED_0 =
    "\4\0\5\1\2\11\10\1\1\11\10\1\1\0\1\11"+
    "\3\0\7\1\1\0\1\11\3\0\1\1\1\0\1\11"+
    "\3\0\23\1";

  private static int [] zzUnpackAttribute() {
    int [] result = new int[70];
    int offset = 0;
    offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAttribute(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }

  /** the input device */
  private java.io.Reader zzReader;

  /** the current state of the DFA */
  private int zzState;

  /** the current lexical state */
  private int zzLexicalState = YYINITIAL;

  /** this buffer contains the current text to be matched and is
      the source of the yytext() string */
  private CharSequence zzBuffer = "";

  /** the textposition at the last accepting state */
  private int zzMarkedPos;

  /** the current text position in the buffer */
  private int zzCurrentPos;

  /** startRead marks the beginning of the yytext() string in the buffer */
  private int zzStartRead;

  /** endRead marks the last character in the buffer, that has been read
      from input */
  private int zzEndRead;

  /**
   * zzAtBOL == true <=> the scanner is currently at the beginning of a line
   */
  private boolean zzAtBOL = true;

  /** zzAtEOF == true <=> the scanner is at the EOF */
  private boolean zzAtEOF;

  /** denotes if the user-EOF-code has already been executed */
  private boolean zzEOFDone;


  /**
   * Creates a new scanner
   *
   * @param   in  the java.io.Reader to read input from.
   */
  LogcatFilterLexer(java.io.Reader in) {
    this.zzReader = in;
  }


  /** 
   * Unpacks the compressed character translation table.
   *
   * @param packed   the packed character translation table
   * @return         the unpacked character translation table
   */
  private static char [] zzUnpackCMap(String packed) {
    int size = 0;
    for (int i = 0, length = packed.length(); i < length; i += 2) {
      size += packed.charAt(i);
    }
    char[] map = new char[size];
    int i = 0;  /* index in packed string  */
    int j = 0;  /* index in unpacked array */
    while (i < packed.length()) {
      int  count = packed.charAt(i++);
      char value = packed.charAt(i++);
      do map[j++] = value; while (--count > 0);
    }
    return map;
  }

  public final int getTokenStart() {
    return zzStartRead;
  }

  public final int getTokenEnd() {
    return getTokenStart() + yylength();
  }

  public void reset(CharSequence buffer, int start, int end, int initialState) {
    zzBuffer = buffer;
    zzCurrentPos = zzMarkedPos = zzStartRead = start;
    zzAtEOF  = false;
    zzAtBOL = true;
    zzEndRead = end;
    yybegin(initialState);
  }

  /**
   * Refills the input buffer.
   *
   * @return      {@code false}, iff there was new input.
   *
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  private boolean zzRefill() throws java.io.IOException {
    return true;
  }


  /**
   * Returns the current lexical state.
   */
  public final int yystate() {
    return zzLexicalState;
  }


  /**
   * Enters a new lexical state
   *
   * @param newState the new lexical state
   */
  public final void yybegin(int newState) {
    zzLexicalState = newState;
  }


  /**
   * Returns the text matched by the current regular expression.
   */
  public final CharSequence yytext() {
    return zzBuffer.subSequence(zzStartRead, zzMarkedPos);
  }


  /**
   * Returns the character at position {@code pos} from the
   * matched text.
   *
   * It is equivalent to yytext().charAt(pos), but faster
   *
   * @param pos the position of the character to fetch.
   *            A value from 0 to yylength()-1.
   *
   * @return the character at position pos
   */
  public final char yycharat(int pos) {
    return zzBuffer.charAt(zzStartRead+pos);
  }


  /**
   * Returns the length of the matched text region.
   */
  public final int yylength() {
    return zzMarkedPos-zzStartRead;
  }


  /**
   * Reports an error that occurred while scanning.
   *
   * In a wellformed scanner (no or only correct usage of
   * yypushback(int) and a match-all fallback rule) this method
   * will only be called with things that "Can't Possibly Happen".
   * If this method is called, something is seriously wrong
   * (e.g. a JFlex bug producing a faulty scanner etc.).
   *
   * Usual syntax/scanner level error handling should be done
   * in error fallback rules.
   *
   * @param   errorCode  the code of the errormessage to display
   */
  private void zzScanError(int errorCode) {
    String message;
    try {
      message = ZZ_ERROR_MSG[errorCode];
    }
    catch (ArrayIndexOutOfBoundsException e) {
      message = ZZ_ERROR_MSG[ZZ_UNKNOWN_ERROR];
    }

    throw new Error(message);
  }


  /**
   * Pushes the specified amount of characters back into the input stream.
   *
   * They will be read again by then next call of the scanning method
   *
   * @param number  the number of characters to be read again.
   *                This number must not be greater than yylength()!
   */
  public void yypushback(int number)  {
    if ( number > yylength() )
      zzScanError(ZZ_PUSHBACK_2BIG);

    zzMarkedPos -= number;
  }


  /**
   * Resumes scanning until the next regular expression is matched,
   * the end of input is encountered or an I/O-Error occurs.
   *
   * @return      the next token
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  public IElementType advance() throws java.io.IOException {
    int zzInput;
    int zzAction;

    // cached fields:
    int zzCurrentPosL;
    int zzMarkedPosL;
    int zzEndReadL = zzEndRead;
    CharSequence zzBufferL = zzBuffer;

    int [] zzTransL = ZZ_TRANS;
    int [] zzRowMapL = ZZ_ROWMAP;
    int [] zzAttrL = ZZ_ATTRIBUTE;

    while (true) {
      zzMarkedPosL = zzMarkedPos;

      zzAction = -1;

      zzCurrentPosL = zzCurrentPos = zzStartRead = zzMarkedPosL;

      zzState = ZZ_LEXSTATE[zzLexicalState];

      // set up zzAction for empty match case:
      int zzAttributes = zzAttrL[zzState];
      if ( (zzAttributes & 1) == 1 ) {
        zzAction = zzState;
      }


      zzForAction: {
        while (true) {

          if (zzCurrentPosL < zzEndReadL) {
            zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL/*, zzEndReadL*/);
            zzCurrentPosL += Character.charCount(zzInput);
          }
          else if (zzAtEOF) {
            zzInput = YYEOF;
            break zzForAction;
          }
          else {
            // store back cached positions
            zzCurrentPos  = zzCurrentPosL;
            zzMarkedPos   = zzMarkedPosL;
            boolean eof = zzRefill();
            // get translated positions and possibly new buffer
            zzCurrentPosL  = zzCurrentPos;
            zzMarkedPosL   = zzMarkedPos;
            zzBufferL      = zzBuffer;
            zzEndReadL     = zzEndRead;
            if (eof) {
              zzInput = YYEOF;
              break zzForAction;
            }
            else {
              zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL/*, zzEndReadL*/);
              zzCurrentPosL += Character.charCount(zzInput);
            }
          }
          int zzNext = zzTransL[ zzRowMapL[zzState] + ZZ_CMAP(zzInput) ];
          if (zzNext == -1) break zzForAction;
          zzState = zzNext;

          zzAttributes = zzAttrL[zzState];
          if ( (zzAttributes & 1) == 1 ) {
            zzAction = zzState;
            zzMarkedPosL = zzCurrentPosL;
            if ( (zzAttributes & 8) == 8 ) break zzForAction;
          }

        }
      }

      // store back cached position
      zzMarkedPos = zzMarkedPosL;

      if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
        zzAtEOF = true;
        return null;
      }
      else {
        switch (zzAction < 0 ? zzAction : ZZ_ACTION[zzAction]) {
          case 1: 
            { return LogcatFilterTypes.VALUE;
            } 
            // fall through
          case 14: break;
          case 2: 
            { return TokenType.WHITE_SPACE;
            } 
            // fall through
          case 15: break;
          case 3: 
            { return LogcatFilterTypes.OR;
            } 
            // fall through
          case 16: break;
          case 4: 
            { return LogcatFilterTypes.AND;
            } 
            // fall through
          case 17: break;
          case 5: 
            { return LogcatFilterTypes.LPAREN;
            } 
            // fall through
          case 18: break;
          case 6: 
            { return LogcatFilterTypes.RPAREN;
            } 
            // fall through
          case 19: break;
          case 7: 
            { return TokenType.BAD_CHARACTER;
            } 
            // fall through
          case 20: break;
          case 8: 
            { yybegin(YYINITIAL); return LogcatFilterTypes.STRING_KVALUE;
            } 
            // fall through
          case 21: break;
          case 9: 
            { yybegin(YYINITIAL); return LogcatFilterTypes.REGEX_KVALUE;
            } 
            // fall through
          case 22: break;
          case 10: 
            { yybegin(YYINITIAL); return LogcatFilterTypes.KVALUE;
            } 
            // fall through
          case 23: break;
          case 11: 
            { yybegin(KVALUE_STATE); return LogcatFilterTypes.KEY;
            } 
            // fall through
          case 24: break;
          case 12: 
            { yybegin(STRING_KVALUE_STATE); return LogcatFilterTypes.STRING_KEY;
            } 
            // fall through
          case 25: break;
          case 13: 
            { yybegin(REGEX_KVALUE_STATE); return LogcatFilterTypes.REGEX_KEY;
            } 
            // fall through
          case 26: break;
          default:
            zzScanError(ZZ_NO_MATCH);
          }
      }
    }
  }


}
