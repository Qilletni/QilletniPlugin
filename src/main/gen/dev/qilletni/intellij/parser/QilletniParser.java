// This is a generated file. Not intended for manual editing.
package dev.qilletni.intellij.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static dev.qilletni.intellij.psi.QilletniTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class QilletniParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return file(b, l + 1);
  }

  /* ********************************************************** */
  // multiplicative_expr ((PLUS|MINUS) multiplicative_expr)*
  public static boolean additive_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "additive_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ADDITIVE_EXPR, "<additive expr>");
    r = multiplicative_expr(b, l + 1);
    r = r && additive_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ((PLUS|MINUS) multiplicative_expr)*
  private static boolean additive_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "additive_expr_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!additive_expr_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "additive_expr_1", c)) break;
    }
    return true;
  }

  // (PLUS|MINUS) multiplicative_expr
  private static boolean additive_expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "additive_expr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = additive_expr_1_0_0(b, l + 1);
    r = r && multiplicative_expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // PLUS|MINUS
  private static boolean additive_expr_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "additive_expr_1_0_0")) return false;
    boolean r;
    r = consumeToken(b, PLUS);
    if (!r) r = consumeToken(b, MINUS);
    return r;
  }

  /* ********************************************************** */
  // album_url_or_name_pair order_settings
  public static boolean album_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "album_expr")) return false;
    if (!nextTokenIs(b, STRING)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = album_url_or_name_pair(b, l + 1);
    r = r && order_settings(b, l + 1);
    exit_section_(b, m, ALBUM_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // STRING ALBUM_TYPE BY STRING
  public static boolean album_url_or_name_pair(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "album_url_or_name_pair")) return false;
    if (!nextTokenIs(b, STRING)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, STRING, ALBUM_TYPE, BY, STRING);
    exit_section_(b, m, ALBUM_URL_OR_NAME_PAIR, r);
    return r;
  }

  /* ********************************************************** */
  // asmt_base (DOUBLE_DOT ID ASSIGN expr)*
  public static boolean asmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ASMT, "<asmt>");
    r = asmt_base(b, l + 1);
    r = r && asmt_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (DOUBLE_DOT ID ASSIGN expr)*
  private static boolean asmt_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asmt_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!asmt_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "asmt_1", c)) break;
    }
    return true;
  }

  // DOUBLE_DOT ID ASSIGN expr
  private static boolean asmt_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asmt_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOUBLE_DOT, ID, ASSIGN);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // var_declaration
  //   | ID LEFT_SBRACKET int_expr RIGHT_SBRACKET ASSIGN expr
  //   | ID ASSIGN expr
  //   | lhs_member ASSIGN expr
  //   | lhs_core DOUBLE_DOT ID ASSIGN expr
  public static boolean asmt_base(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asmt_base")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ASMT_BASE, "<asmt base>");
    r = var_declaration(b, l + 1);
    if (!r) r = asmt_base_1(b, l + 1);
    if (!r) r = asmt_base_2(b, l + 1);
    if (!r) r = asmt_base_3(b, l + 1);
    if (!r) r = asmt_base_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ID LEFT_SBRACKET int_expr RIGHT_SBRACKET ASSIGN expr
  private static boolean asmt_base_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asmt_base_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ID, LEFT_SBRACKET);
    r = r && int_expr(b, l + 1);
    r = r && consumeTokens(b, 0, RIGHT_SBRACKET, ASSIGN);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ID ASSIGN expr
  private static boolean asmt_base_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asmt_base_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ID, ASSIGN);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lhs_member ASSIGN expr
  private static boolean asmt_base_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asmt_base_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lhs_member(b, l + 1);
    r = r && consumeToken(b, ASSIGN);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // lhs_core DOUBLE_DOT ID ASSIGN expr
  private static boolean asmt_base_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asmt_base_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lhs_core(b, l + 1);
    r = r && consumeTokens(b, 0, DOUBLE_DOT, ID, ASSIGN);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // body_stmt* return_stmt?
  public static boolean body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "body")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BODY, "<body>");
    r = body_0(b, l + 1);
    r = r && body_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // body_stmt*
  private static boolean body_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "body_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!body_stmt(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "body_0", c)) break;
    }
    return true;
  }

  // return_stmt?
  private static boolean body_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "body_1")) return false;
    return_stmt(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // if_stmt | for_stmt | stmt | expr | LINE_COMMENT | BLOCK_COMMENT | DOC_COMMENT
  public static boolean body_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "body_stmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BODY_STMT, "<body stmt>");
    r = if_stmt(b, l + 1);
    if (!r) r = for_stmt(b, l + 1);
    if (!r) r = stmt(b, l + 1);
    if (!r) r = expr(b, l + 1);
    if (!r) r = consumeToken(b, LINE_COMMENT);
    if (!r) r = consumeToken(b, BLOCK_COMMENT);
    if (!r) r = consumeToken(b, DOC_COMMENT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // collection_url_or_name_pair order_settings
  //                   | COLLECTION_TYPE LEFT_PAREN (list_expression | ID) RIGHT_PAREN order_settings
  public static boolean collection_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "collection_expr")) return false;
    if (!nextTokenIs(b, "<collection expr>", COLLECTION_TYPE, STRING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, COLLECTION_EXPR, "<collection expr>");
    r = collection_expr_0(b, l + 1);
    if (!r) r = collection_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // collection_url_or_name_pair order_settings
  private static boolean collection_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "collection_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = collection_url_or_name_pair(b, l + 1);
    r = r && order_settings(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COLLECTION_TYPE LEFT_PAREN (list_expression | ID) RIGHT_PAREN order_settings
  private static boolean collection_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "collection_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COLLECTION_TYPE, LEFT_PAREN);
    r = r && collection_expr_1_2(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    r = r && order_settings(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // list_expression | ID
  private static boolean collection_expr_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "collection_expr_1_2")) return false;
    boolean r;
    r = list_expression(b, l + 1);
    if (!r) r = consumeToken(b, ID);
    return r;
  }

  /* ********************************************************** */
  // LIMIT_PARAM LEFT_SBRACKET limit_amount RIGHT_SBRACKET
  public static boolean collection_limit(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "collection_limit")) return false;
    if (!nextTokenIs(b, LIMIT_PARAM)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LIMIT_PARAM, LEFT_SBRACKET);
    r = r && limit_amount(b, l + 1);
    r = r && consumeToken(b, RIGHT_SBRACKET);
    exit_section_(b, m, COLLECTION_LIMIT, r);
    return r;
  }

  /* ********************************************************** */
  // STRING COLLECTION_TYPE BY STRING
  public static boolean collection_url_or_name_pair(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "collection_url_or_name_pair")) return false;
    if (!nextTokenIs(b, STRING)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, STRING, COLLECTION_TYPE, BY, STRING);
    exit_section_(b, m, COLLECTION_URL_OR_NAME_PAIR, r);
    return r;
  }

  /* ********************************************************** */
  // ID
  public static boolean constructor_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constructor_name")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, CONSTRUCTOR_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // DOUBLE | DOUBLE_TYPE LEFT_PAREN expr RIGHT_PAREN
  public static boolean double_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "double_expr")) return false;
    if (!nextTokenIs(b, "<double expr>", DOUBLE, DOUBLE_TYPE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DOUBLE_EXPR, "<double expr>");
    r = consumeToken(b, DOUBLE);
    if (!r) r = double_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // DOUBLE_TYPE LEFT_PAREN expr RIGHT_PAREN
  private static boolean double_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "double_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOUBLE_TYPE, LEFT_PAREN);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ELSE_KEYWORD LEFT_CBRACKET body RIGHT_CBRACKET
  public static boolean else_body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "else_body")) return false;
    if (!nextTokenIs(b, ELSE_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ELSE_KEYWORD, LEFT_CBRACKET);
    r = r && body(b, l + 1);
    r = r && consumeToken(b, RIGHT_CBRACKET);
    exit_section_(b, m, ELSE_BODY, r);
    return r;
  }

  /* ********************************************************** */
  // ELSE_KEYWORD IF_KEYWORD LEFT_PAREN expr RIGHT_PAREN LEFT_CBRACKET body RIGHT_CBRACKET
  public static boolean elseif_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elseif_list")) return false;
    if (!nextTokenIs(b, ELSE_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ELSE_KEYWORD, IF_KEYWORD, LEFT_PAREN);
    r = r && expr(b, l + 1);
    r = r && consumeTokens(b, 0, RIGHT_PAREN, LEFT_CBRACKET);
    r = r && body(b, l + 1);
    r = r && consumeToken(b, RIGHT_CBRACKET);
    exit_section_(b, m, ELSEIF_LIST, r);
    return r;
  }

  /* ********************************************************** */
  // entity_property_declaration* entity_constructor? function_def*
  public static boolean entity_body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_body")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENTITY_BODY, "<entity body>");
    r = entity_body_0(b, l + 1);
    r = r && entity_body_1(b, l + 1);
    r = r && entity_body_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // entity_property_declaration*
  private static boolean entity_body_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_body_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!entity_property_declaration(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "entity_body_0", c)) break;
    }
    return true;
  }

  // entity_constructor?
  private static boolean entity_body_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_body_1")) return false;
    entity_constructor(b, l + 1);
    return true;
  }

  // function_def*
  private static boolean entity_body_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_body_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!function_def(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "entity_body_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // DOC_COMMENT? constructor_name LEFT_PAREN function_def_params RIGHT_PAREN
  public static boolean entity_constructor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_constructor")) return false;
    if (!nextTokenIs(b, "<entity constructor>", DOC_COMMENT, ID)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENTITY_CONSTRUCTOR, "<entity constructor>");
    r = entity_constructor_0(b, l + 1);
    r = r && constructor_name(b, l + 1);
    r = r && consumeToken(b, LEFT_PAREN);
    r = r && function_def_params(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // DOC_COMMENT?
  private static boolean entity_constructor_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_constructor_0")) return false;
    consumeToken(b, DOC_COMMENT);
    return true;
  }

  /* ********************************************************** */
  // DOC_COMMENT? ENTITY entity_name LEFT_CBRACKET entity_body RIGHT_CBRACKET
  public static boolean entity_def(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_def")) return false;
    if (!nextTokenIs(b, "<entity def>", DOC_COMMENT, ENTITY)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENTITY_DEF, "<entity def>");
    r = entity_def_0(b, l + 1);
    r = r && consumeToken(b, ENTITY);
    r = r && entity_name(b, l + 1);
    r = r && consumeToken(b, LEFT_CBRACKET);
    r = r && entity_body(b, l + 1);
    r = r && consumeToken(b, RIGHT_CBRACKET);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // DOC_COMMENT?
  private static boolean entity_def_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_def_0")) return false;
    consumeToken(b, DOC_COMMENT);
    return true;
  }

  /* ********************************************************** */
  // NEW ID LEFT_PAREN expr_list? RIGHT_PAREN
  public static boolean entity_initialize(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_initialize")) return false;
    if (!nextTokenIs(b, NEW)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, NEW, ID, LEFT_PAREN);
    r = r && entity_initialize_3(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    exit_section_(b, m, ENTITY_INITIALIZE, r);
    return r;
  }

  // expr_list?
  private static boolean entity_initialize_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_initialize_3")) return false;
    expr_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // ID
  public static boolean entity_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_name")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, ENTITY_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // DOC_COMMENT?
  //                                 (ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID)
  //                                 (LEFT_SBRACKET RIGHT_SBRACKET)? property_name (ASSIGN expr)?
  public static boolean entity_property_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_property_declaration")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENTITY_PROPERTY_DECLARATION, "<entity property declaration>");
    r = entity_property_declaration_0(b, l + 1);
    r = r && entity_property_declaration_1(b, l + 1);
    r = r && entity_property_declaration_2(b, l + 1);
    r = r && property_name(b, l + 1);
    r = r && entity_property_declaration_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // DOC_COMMENT?
  private static boolean entity_property_declaration_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_property_declaration_0")) return false;
    consumeToken(b, DOC_COMMENT);
    return true;
  }

  // ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID
  private static boolean entity_property_declaration_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_property_declaration_1")) return false;
    boolean r;
    r = consumeToken(b, ANY_TYPE);
    if (!r) r = consumeToken(b, INT_TYPE);
    if (!r) r = consumeToken(b, DOUBLE_TYPE);
    if (!r) r = consumeToken(b, STRING_TYPE);
    if (!r) r = consumeToken(b, BOOLEAN_TYPE);
    if (!r) r = consumeToken(b, COLLECTION_TYPE);
    if (!r) r = consumeToken(b, SONG_TYPE);
    if (!r) r = consumeToken(b, WEIGHTS_KEYWORD);
    if (!r) r = consumeToken(b, ALBUM_TYPE);
    if (!r) r = consumeToken(b, JAVA_TYPE);
    if (!r) r = consumeToken(b, ID);
    return r;
  }

  // (LEFT_SBRACKET RIGHT_SBRACKET)?
  private static boolean entity_property_declaration_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_property_declaration_2")) return false;
    entity_property_declaration_2_0(b, l + 1);
    return true;
  }

  // LEFT_SBRACKET RIGHT_SBRACKET
  private static boolean entity_property_declaration_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_property_declaration_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LEFT_SBRACKET, RIGHT_SBRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  // (ASSIGN expr)?
  private static boolean entity_property_declaration_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_property_declaration_4")) return false;
    entity_property_declaration_4_0(b, l + 1);
    return true;
  }

  // ASSIGN expr
  private static boolean entity_property_declaration_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "entity_property_declaration_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ASSIGN);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // logical_or_expr
  public static boolean expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, EXPR, "<expr>");
    r = logical_or_expr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // expr (COMMA expr)*
  public static boolean expr_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, EXPR_LIST, "<expr list>");
    r = expr(b, l + 1);
    r = r && expr_list_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (COMMA expr)*
  private static boolean expr_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!expr_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "expr_list_1", c)) break;
    }
    return true;
  }

  // COMMA expr
  private static boolean expr_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "expr_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // prog
  static boolean file(PsiBuilder b, int l) {
    return prog(b, l + 1);
  }

  /* ********************************************************** */
  // expr | range_expr | foreach_range
  public static boolean for_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FOR_EXPR, "<for expr>");
    r = expr(b, l + 1);
    if (!r) r = range_expr(b, l + 1);
    if (!r) r = foreach_range(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // FOR_KEYWORD LEFT_PAREN for_expr RIGHT_PAREN LEFT_CBRACKET body RIGHT_CBRACKET
  public static boolean for_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_stmt")) return false;
    if (!nextTokenIs(b, FOR_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR_KEYWORD, LEFT_PAREN);
    r = r && for_expr(b, l + 1);
    r = r && consumeTokens(b, 0, RIGHT_PAREN, LEFT_CBRACKET);
    r = r && body(b, l + 1);
    r = r && consumeToken(b, RIGHT_CBRACKET);
    exit_section_(b, m, FOR_STMT, r);
    return r;
  }

  /* ********************************************************** */
  // ID COLON expr
  public static boolean foreach_range(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "foreach_range")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ID, COLON);
    r = r && expr(b, l + 1);
    exit_section_(b, m, FOREACH_RANGE, r);
    return r;
  }

  /* ********************************************************** */
  // ID LEFT_PAREN expr_list? RIGHT_PAREN
  public static boolean function_call(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_call")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ID, LEFT_PAREN);
    r = r && function_call_2(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    exit_section_(b, m, FUNCTION_CALL, r);
    return r;
  }

  // expr_list?
  private static boolean function_call_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_call_2")) return false;
    expr_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // DOC_COMMENT? STATIC? FUNCTION_DEF function_name LEFT_PAREN function_def_params RIGHT_PAREN function_on_type? LEFT_CBRACKET body RIGHT_CBRACKET
  //                | DOC_COMMENT? NATIVE STATIC? FUNCTION_DEF function_name LEFT_PAREN function_def_params RIGHT_PAREN function_on_type?
  public static boolean function_def(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_DEFINITION, "<function def>");
    r = function_def_0(b, l + 1);
    if (!r) r = function_def_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // DOC_COMMENT? STATIC? FUNCTION_DEF function_name LEFT_PAREN function_def_params RIGHT_PAREN function_on_type? LEFT_CBRACKET body RIGHT_CBRACKET
  private static boolean function_def_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = function_def_0_0(b, l + 1);
    r = r && function_def_0_1(b, l + 1);
    r = r && consumeToken(b, FUNCTION_DEF);
    r = r && function_name(b, l + 1);
    r = r && consumeToken(b, LEFT_PAREN);
    r = r && function_def_params(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    r = r && function_def_0_7(b, l + 1);
    r = r && consumeToken(b, LEFT_CBRACKET);
    r = r && body(b, l + 1);
    r = r && consumeToken(b, RIGHT_CBRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  // DOC_COMMENT?
  private static boolean function_def_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_0_0")) return false;
    consumeToken(b, DOC_COMMENT);
    return true;
  }

  // STATIC?
  private static boolean function_def_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_0_1")) return false;
    consumeToken(b, STATIC);
    return true;
  }

  // function_on_type?
  private static boolean function_def_0_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_0_7")) return false;
    function_on_type(b, l + 1);
    return true;
  }

  // DOC_COMMENT? NATIVE STATIC? FUNCTION_DEF function_name LEFT_PAREN function_def_params RIGHT_PAREN function_on_type?
  private static boolean function_def_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = function_def_1_0(b, l + 1);
    r = r && consumeToken(b, NATIVE);
    r = r && function_def_1_2(b, l + 1);
    r = r && consumeToken(b, FUNCTION_DEF);
    r = r && function_name(b, l + 1);
    r = r && consumeToken(b, LEFT_PAREN);
    r = r && function_def_params(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    r = r && function_def_1_8(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // DOC_COMMENT?
  private static boolean function_def_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_1_0")) return false;
    consumeToken(b, DOC_COMMENT);
    return true;
  }

  // STATIC?
  private static boolean function_def_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_1_2")) return false;
    consumeToken(b, STATIC);
    return true;
  }

  // function_on_type?
  private static boolean function_def_1_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_1_8")) return false;
    function_on_type(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (param_name (COMMA param_name)*)?
  public static boolean function_def_params(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_params")) return false;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_DEF_PARAMS, "<function def params>");
    function_def_params_0(b, l + 1);
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // param_name (COMMA param_name)*
  private static boolean function_def_params_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_params_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = param_name(b, l + 1);
    r = r && function_def_params_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA param_name)*
  private static boolean function_def_params_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_params_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!function_def_params_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "function_def_params_0_1", c)) break;
    }
    return true;
  }

  // COMMA param_name
  private static boolean function_def_params_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_params_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && param_name(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ID
  public static boolean function_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_name")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, FUNCTION_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // ON (INT_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|ALBUM_TYPE|WEIGHTS_KEYWORD|ID)
  public static boolean function_on_type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_on_type")) return false;
    if (!nextTokenIs(b, ON)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ON);
    r = r && function_on_type_1(b, l + 1);
    exit_section_(b, m, FUNCTION_ON_TYPE, r);
    return r;
  }

  // INT_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|ALBUM_TYPE|WEIGHTS_KEYWORD|ID
  private static boolean function_on_type_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_on_type_1")) return false;
    boolean r;
    r = consumeToken(b, INT_TYPE);
    if (!r) r = consumeToken(b, STRING_TYPE);
    if (!r) r = consumeToken(b, BOOLEAN_TYPE);
    if (!r) r = consumeToken(b, COLLECTION_TYPE);
    if (!r) r = consumeToken(b, SONG_TYPE);
    if (!r) r = consumeToken(b, ALBUM_TYPE);
    if (!r) r = consumeToken(b, WEIGHTS_KEYWORD);
    if (!r) r = consumeToken(b, ID);
    return r;
  }

  /* ********************************************************** */
  // IF_KEYWORD LEFT_PAREN expr RIGHT_PAREN LEFT_CBRACKET body RIGHT_CBRACKET elseif_list* else_body?
  public static boolean if_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_stmt")) return false;
    if (!nextTokenIs(b, IF_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IF_KEYWORD, LEFT_PAREN);
    r = r && expr(b, l + 1);
    r = r && consumeTokens(b, 0, RIGHT_PAREN, LEFT_CBRACKET);
    r = r && body(b, l + 1);
    r = r && consumeToken(b, RIGHT_CBRACKET);
    r = r && if_stmt_7(b, l + 1);
    r = r && if_stmt_8(b, l + 1);
    exit_section_(b, m, IF_STMT, r);
    return r;
  }

  // elseif_list*
  private static boolean if_stmt_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_stmt_7")) return false;
    while (true) {
      int c = current_position_(b);
      if (!elseif_list(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "if_stmt_7", c)) break;
    }
    return true;
  }

  // else_body?
  private static boolean if_stmt_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_stmt_8")) return false;
    else_body(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (INCREMENT|DECREMENT) | ((PLUS_EQUALS|MINUS_EQUALS) expr)
  public static boolean immutable_postfix_expr_suffix(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "immutable_postfix_expr_suffix")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, IMMUTABLE_POSTFIX_EXPR_SUFFIX, "<immutable postfix expr suffix>");
    r = immutable_postfix_expr_suffix_0(b, l + 1);
    if (!r) r = immutable_postfix_expr_suffix_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // INCREMENT|DECREMENT
  private static boolean immutable_postfix_expr_suffix_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "immutable_postfix_expr_suffix_0")) return false;
    boolean r;
    r = consumeToken(b, INCREMENT);
    if (!r) r = consumeToken(b, DECREMENT);
    return r;
  }

  // (PLUS_EQUALS|MINUS_EQUALS) expr
  private static boolean immutable_postfix_expr_suffix_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "immutable_postfix_expr_suffix_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = immutable_postfix_expr_suffix_1_0(b, l + 1);
    r = r && expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // PLUS_EQUALS|MINUS_EQUALS
  private static boolean immutable_postfix_expr_suffix_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "immutable_postfix_expr_suffix_1_0")) return false;
    boolean r;
    r = consumeToken(b, PLUS_EQUALS);
    if (!r) r = consumeToken(b, MINUS_EQUALS);
    return r;
  }

  /* ********************************************************** */
  // IMPORT STRING (AS ID)?
  public static boolean import_file(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_file")) return false;
    if (!nextTokenIs(b, IMPORT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, IMPORT, STRING);
    r = r && import_file_2(b, l + 1);
    exit_section_(b, m, IMPORT_FILE, r);
    return r;
  }

  // (AS ID)?
  private static boolean import_file_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_file_2")) return false;
    import_file_2_0(b, l + 1);
    return true;
  }

  // AS ID
  private static boolean import_file_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "import_file_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, AS, ID);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // INT | INT_TYPE LEFT_PAREN expr RIGHT_PAREN
  public static boolean int_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "int_expr")) return false;
    if (!nextTokenIs(b, "<int expr>", INT, INT_TYPE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, INT_EXPR, "<int expr>");
    r = consumeToken(b, INT);
    if (!r) r = int_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // INT_TYPE LEFT_PAREN expr RIGHT_PAREN
  private static boolean int_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "int_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, INT_TYPE, LEFT_PAREN);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ID IS_KEYWORD ((ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID)? (LEFT_SBRACKET RIGHT_SBRACKET)?)
  public static boolean is_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "is_expr")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ID, IS_KEYWORD);
    r = r && is_expr_2(b, l + 1);
    exit_section_(b, m, IS_EXPR, r);
    return r;
  }

  // (ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID)? (LEFT_SBRACKET RIGHT_SBRACKET)?
  private static boolean is_expr_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "is_expr_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = is_expr_2_0(b, l + 1);
    r = r && is_expr_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID)?
  private static boolean is_expr_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "is_expr_2_0")) return false;
    is_expr_2_0_0(b, l + 1);
    return true;
  }

  // ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID
  private static boolean is_expr_2_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "is_expr_2_0_0")) return false;
    boolean r;
    r = consumeToken(b, ANY_TYPE);
    if (!r) r = consumeToken(b, INT_TYPE);
    if (!r) r = consumeToken(b, DOUBLE_TYPE);
    if (!r) r = consumeToken(b, STRING_TYPE);
    if (!r) r = consumeToken(b, BOOLEAN_TYPE);
    if (!r) r = consumeToken(b, COLLECTION_TYPE);
    if (!r) r = consumeToken(b, SONG_TYPE);
    if (!r) r = consumeToken(b, WEIGHTS_KEYWORD);
    if (!r) r = consumeToken(b, ALBUM_TYPE);
    if (!r) r = consumeToken(b, JAVA_TYPE);
    if (!r) r = consumeToken(b, ID);
    return r;
  }

  // (LEFT_SBRACKET RIGHT_SBRACKET)?
  private static boolean is_expr_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "is_expr_2_1")) return false;
    is_expr_2_1_0(b, l + 1);
    return true;
  }

  // LEFT_SBRACKET RIGHT_SBRACKET
  private static boolean is_expr_2_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "is_expr_2_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LEFT_SBRACKET, RIGHT_SBRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // EMPTY
  public static boolean java_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "java_expr")) return false;
    if (!nextTokenIs(b, EMPTY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EMPTY);
    exit_section_(b, m, JAVA_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // primary_expr (DOT function_call)*
  public static boolean lhs_core(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lhs_core")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LHS_CORE, "<lhs core>");
    r = primary_expr(b, l + 1);
    r = r && lhs_core_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (DOT function_call)*
  private static boolean lhs_core_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lhs_core_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!lhs_core_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "lhs_core_1", c)) break;
    }
    return true;
  }

  // DOT function_call
  private static boolean lhs_core_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lhs_core_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && function_call(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // lhs_core (DOT ID)+
  public static boolean lhs_member(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lhs_member")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LHS_MEMBER, "<lhs member>");
    r = lhs_core(b, l + 1);
    r = r && lhs_member_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (DOT ID)+
  private static boolean lhs_member_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lhs_member_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lhs_member_1_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!lhs_member_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "lhs_member_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // DOT ID
  private static boolean lhs_member_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lhs_member_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOT, ID);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // INT LIMIT_UNIT?
  public static boolean limit_amount(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "limit_amount")) return false;
    if (!nextTokenIs(b, INT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, INT);
    r = r && limit_amount_1(b, l + 1);
    exit_section_(b, m, LIMIT_AMOUNT, r);
    return r;
  }

  // LIMIT_UNIT?
  private static boolean limit_amount_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "limit_amount_1")) return false;
    consumeToken(b, LIMIT_UNIT);
    return true;
  }

  /* ********************************************************** */
  // (ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID)?
  //                     LEFT_SBRACKET expr_list? RIGHT_SBRACKET
  public static boolean list_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_expression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIST_EXPRESSION, "<list expression>");
    r = list_expression_0(b, l + 1);
    r = r && consumeToken(b, LEFT_SBRACKET);
    r = r && list_expression_2(b, l + 1);
    r = r && consumeToken(b, RIGHT_SBRACKET);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID)?
  private static boolean list_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_expression_0")) return false;
    list_expression_0_0(b, l + 1);
    return true;
  }

  // ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID
  private static boolean list_expression_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_expression_0_0")) return false;
    boolean r;
    r = consumeToken(b, ANY_TYPE);
    if (!r) r = consumeToken(b, INT_TYPE);
    if (!r) r = consumeToken(b, DOUBLE_TYPE);
    if (!r) r = consumeToken(b, STRING_TYPE);
    if (!r) r = consumeToken(b, BOOLEAN_TYPE);
    if (!r) r = consumeToken(b, COLLECTION_TYPE);
    if (!r) r = consumeToken(b, SONG_TYPE);
    if (!r) r = consumeToken(b, WEIGHTS_KEYWORD);
    if (!r) r = consumeToken(b, ALBUM_TYPE);
    if (!r) r = consumeToken(b, JAVA_TYPE);
    if (!r) r = consumeToken(b, ID);
    return r;
  }

  // expr_list?
  private static boolean list_expression_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_expression_2")) return false;
    expr_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // relational_expr (ANDAND relational_expr)*
  public static boolean logical_and_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_and_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LOGICAL_AND_EXPR, "<logical and expr>");
    r = relational_expr(b, l + 1);
    r = r && logical_and_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (ANDAND relational_expr)*
  private static boolean logical_and_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_and_expr_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!logical_and_expr_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "logical_and_expr_1", c)) break;
    }
    return true;
  }

  // ANDAND relational_expr
  private static boolean logical_and_expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_and_expr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ANDAND);
    r = r && relational_expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // logical_and_expr (OROR logical_and_expr)*
  public static boolean logical_or_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_or_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LOGICAL_OR_EXPR, "<logical or expr>");
    r = logical_and_expr(b, l + 1);
    r = r && logical_or_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (OROR logical_and_expr)*
  private static boolean logical_or_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_or_expr_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!logical_or_expr_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "logical_or_expr_1", c)) break;
    }
    return true;
  }

  // OROR logical_and_expr
  private static boolean logical_or_expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_or_expr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OROR);
    r = r && logical_and_expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // unary_expr ((STAR|FLOOR_DIV|DIV|MOD) unary_expr)*
  public static boolean multiplicative_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiplicative_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MULTIPLICATIVE_EXPR, "<multiplicative expr>");
    r = unary_expr(b, l + 1);
    r = r && multiplicative_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ((STAR|FLOOR_DIV|DIV|MOD) unary_expr)*
  private static boolean multiplicative_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiplicative_expr_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!multiplicative_expr_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "multiplicative_expr_1", c)) break;
    }
    return true;
  }

  // (STAR|FLOOR_DIV|DIV|MOD) unary_expr
  private static boolean multiplicative_expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiplicative_expr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = multiplicative_expr_1_0_0(b, l + 1);
    r = r && unary_expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // STAR|FLOOR_DIV|DIV|MOD
  private static boolean multiplicative_expr_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "multiplicative_expr_1_0_0")) return false;
    boolean r;
    r = consumeToken(b, STAR);
    if (!r) r = consumeToken(b, FLOOR_DIV);
    if (!r) r = consumeToken(b, DIV);
    if (!r) r = consumeToken(b, MOD);
    return r;
  }

  /* ********************************************************** */
  // ORDER_PARAM LEFT_SBRACKET COLLECTION_ORDER RIGHT_SBRACKET
  public static boolean order_define(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "order_define")) return false;
    if (!nextTokenIs(b, ORDER_PARAM)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ORDER_PARAM, LEFT_SBRACKET, COLLECTION_ORDER, RIGHT_SBRACKET);
    exit_section_(b, m, ORDER_DEFINE, r);
    return r;
  }

  /* ********************************************************** */
  // order_define? weights_define?
  public static boolean order_settings(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "order_settings")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ORDER_SETTINGS, "<order settings>");
    r = order_settings_0(b, l + 1);
    r = r && order_settings_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // order_define?
  private static boolean order_settings_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "order_settings_0")) return false;
    order_define(b, l + 1);
    return true;
  }

  // weights_define?
  private static boolean order_settings_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "order_settings_1")) return false;
    weights_define(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // ID
  public static boolean param_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "param_name")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, PARAM_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // PLAY (ID|expr) collection_limit? LOOP_PARAM?
  public static boolean play_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "play_stmt")) return false;
    if (!nextTokenIs(b, PLAY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PLAY);
    r = r && play_stmt_1(b, l + 1);
    r = r && play_stmt_2(b, l + 1);
    r = r && play_stmt_3(b, l + 1);
    exit_section_(b, m, PLAY_STMT, r);
    return r;
  }

  // ID|expr
  private static boolean play_stmt_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "play_stmt_1")) return false;
    boolean r;
    r = consumeToken(b, ID);
    if (!r) r = expr(b, l + 1);
    return r;
  }

  // collection_limit?
  private static boolean play_stmt_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "play_stmt_2")) return false;
    collection_limit(b, l + 1);
    return true;
  }

  // LOOP_PARAM?
  private static boolean play_stmt_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "play_stmt_3")) return false;
    consumeToken(b, LOOP_PARAM);
    return true;
  }

  /* ********************************************************** */
  // primary_expr (DOT postfix_suffix)*
  public static boolean postfix_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "postfix_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, POSTFIX_EXPR, "<postfix expr>");
    r = primary_expr(b, l + 1);
    r = r && postfix_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (DOT postfix_suffix)*
  private static boolean postfix_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "postfix_expr_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!postfix_expr_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "postfix_expr_1", c)) break;
    }
    return true;
  }

  // DOT postfix_suffix
  private static boolean postfix_expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "postfix_expr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && postfix_suffix(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // function_call | ID
  public static boolean postfix_suffix(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "postfix_suffix")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = function_call(b, l + 1);
    if (!r) r = consumeToken(b, ID);
    exit_section_(b, m, POSTFIX_SUFFIX, r);
    return r;
  }

  /* ********************************************************** */
  // LEFT_PAREN expr RIGHT_PAREN
  //                | function_call
  //                | BOOL
  //                | list_expression
  //                | entity_initialize
  //                | int_expr
  //                | double_expr
  //                | song_expr  // These music types differ from ANTLR grammar by not accepting a STRING directly. This is because it causes parsing issues in bnf
  //                | album_expr
  //                | collection_expr
  //                | str_expr
  //                | weights_expr
  //                | java_expr
  //                | is_expr
  //                | ID
  public static boolean primary_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primary_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PRIMARY_EXPR, "<primary expr>");
    r = primary_expr_0(b, l + 1);
    if (!r) r = function_call(b, l + 1);
    if (!r) r = consumeToken(b, BOOL);
    if (!r) r = list_expression(b, l + 1);
    if (!r) r = entity_initialize(b, l + 1);
    if (!r) r = int_expr(b, l + 1);
    if (!r) r = double_expr(b, l + 1);
    if (!r) r = song_expr(b, l + 1);
    if (!r) r = album_expr(b, l + 1);
    if (!r) r = collection_expr(b, l + 1);
    if (!r) r = str_expr(b, l + 1);
    if (!r) r = weights_expr(b, l + 1);
    if (!r) r = java_expr(b, l + 1);
    if (!r) r = is_expr(b, l + 1);
    if (!r) r = consumeToken(b, ID);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // LEFT_PAREN expr RIGHT_PAREN
  private static boolean primary_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primary_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_PAREN);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (LINE_COMMENT | BLOCK_COMMENT | DOC_COMMENT | import_file | running)*
  public static boolean prog(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prog")) return false;
    Marker m = enter_section_(b, l, _NONE_, PROG, "<prog>");
    while (true) {
      int c = current_position_(b);
      if (!prog_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "prog", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // LINE_COMMENT | BLOCK_COMMENT | DOC_COMMENT | import_file | running
  private static boolean prog_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prog_0")) return false;
    boolean r;
    r = consumeToken(b, LINE_COMMENT);
    if (!r) r = consumeToken(b, BLOCK_COMMENT);
    if (!r) r = consumeToken(b, DOC_COMMENT);
    if (!r) r = import_file(b, l + 1);
    if (!r) r = running(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // ID
  public static boolean property_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_name")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, PROPERTY_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // PROVIDER str_expr (LEFT_CBRACKET body RIGHT_CBRACKET)?
  public static boolean provider_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "provider_stmt")) return false;
    if (!nextTokenIs(b, PROVIDER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PROVIDER);
    r = r && str_expr(b, l + 1);
    r = r && provider_stmt_2(b, l + 1);
    exit_section_(b, m, PROVIDER_STMT, r);
    return r;
  }

  // (LEFT_CBRACKET body RIGHT_CBRACKET)?
  private static boolean provider_stmt_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "provider_stmt_2")) return false;
    provider_stmt_2_0(b, l + 1);
    return true;
  }

  // LEFT_CBRACKET body RIGHT_CBRACKET
  private static boolean provider_stmt_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "provider_stmt_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_CBRACKET);
    r = r && body(b, l + 1);
    r = r && consumeToken(b, RIGHT_CBRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ID DOUBLE_DOT (expr | RANGE_INFINITY)
  public static boolean range_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "range_expr")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ID, DOUBLE_DOT);
    r = r && range_expr_2(b, l + 1);
    exit_section_(b, m, RANGE_EXPR, r);
    return r;
  }

  // expr | RANGE_INFINITY
  private static boolean range_expr_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "range_expr_2")) return false;
    boolean r;
    r = expr(b, l + 1);
    if (!r) r = consumeToken(b, RANGE_INFINITY);
    return r;
  }

  /* ********************************************************** */
  // additive_expr (REL_OP additive_expr)?
  public static boolean relational_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, RELATIONAL_EXPR, "<relational expr>");
    r = additive_expr(b, l + 1);
    r = r && relational_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (REL_OP additive_expr)?
  private static boolean relational_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_expr_1")) return false;
    relational_expr_1_0(b, l + 1);
    return true;
  }

  // REL_OP additive_expr
  private static boolean relational_expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relational_expr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REL_OP);
    r = r && additive_expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // RETURN expr
  public static boolean return_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "return_stmt")) return false;
    if (!nextTokenIs(b, RETURN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, RETURN);
    r = r && expr(b, l + 1);
    exit_section_(b, m, RETURN_STMT, r);
    return r;
  }

  /* ********************************************************** */
  // body_stmt | function_def
  public static boolean running(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "running")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, RUNNING, "<running>");
    r = body_stmt(b, l + 1);
    if (!r) r = function_def(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // WEIGHT_PIPE weight_amount expr
  public static boolean single_weight(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "single_weight")) return false;
    if (!nextTokenIs(b, WEIGHT_PIPE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, WEIGHT_PIPE);
    r = r && weight_amount(b, l + 1);
    r = r && expr(b, l + 1);
    exit_section_(b, m, SINGLE_WEIGHT, r);
    return r;
  }

  /* ********************************************************** */
  // song_url_or_name_pair
  public static boolean song_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "song_expr")) return false;
    if (!nextTokenIs(b, STRING)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = song_url_or_name_pair(b, l + 1);
    exit_section_(b, m, SONG_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // STRING (SONG_TYPE)? BY STRING
  public static boolean song_url_or_name_pair(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "song_url_or_name_pair")) return false;
    if (!nextTokenIs(b, STRING)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STRING);
    r = r && song_url_or_name_pair_1(b, l + 1);
    r = r && consumeTokens(b, 0, BY, STRING);
    exit_section_(b, m, SONG_URL_OR_NAME_PAIR, r);
    return r;
  }

  // (SONG_TYPE)?
  private static boolean song_url_or_name_pair_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "song_url_or_name_pair_1")) return false;
    consumeToken(b, SONG_TYPE);
    return true;
  }

  /* ********************************************************** */
  // play_stmt | asmt | entity_def | provider_stmt
  public static boolean stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STMT, "<stmt>");
    r = play_stmt(b, l + 1);
    if (!r) r = asmt(b, l + 1);
    if (!r) r = entity_def(b, l + 1);
    if (!r) r = provider_stmt(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // STRING | STRING_TYPE LEFT_PAREN expr RIGHT_PAREN
  public static boolean str_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "str_expr")) return false;
    if (!nextTokenIs(b, "<str expr>", STRING, STRING_TYPE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STR_EXPR, "<str expr>");
    r = consumeToken(b, STRING);
    if (!r) r = str_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // STRING_TYPE LEFT_PAREN expr RIGHT_PAREN
  private static boolean str_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "str_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, STRING_TYPE, LEFT_PAREN);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // NOT unary_expr
  //              | (INCREMENT|DECREMENT)? postfix_expr (LEFT_SBRACKET expr RIGHT_SBRACKET)? immutable_postfix_expr_suffix?
  public static boolean unary_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UNARY_EXPR, "<unary expr>");
    r = unary_expr_0(b, l + 1);
    if (!r) r = unary_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // NOT unary_expr
  private static boolean unary_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NOT);
    r = r && unary_expr(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (INCREMENT|DECREMENT)? postfix_expr (LEFT_SBRACKET expr RIGHT_SBRACKET)? immutable_postfix_expr_suffix?
  private static boolean unary_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unary_expr_1_0(b, l + 1);
    r = r && postfix_expr(b, l + 1);
    r = r && unary_expr_1_2(b, l + 1);
    r = r && unary_expr_1_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (INCREMENT|DECREMENT)?
  private static boolean unary_expr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_1_0")) return false;
    unary_expr_1_0_0(b, l + 1);
    return true;
  }

  // INCREMENT|DECREMENT
  private static boolean unary_expr_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_1_0_0")) return false;
    boolean r;
    r = consumeToken(b, INCREMENT);
    if (!r) r = consumeToken(b, DECREMENT);
    return r;
  }

  // (LEFT_SBRACKET expr RIGHT_SBRACKET)?
  private static boolean unary_expr_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_1_2")) return false;
    unary_expr_1_2_0(b, l + 1);
    return true;
  }

  // LEFT_SBRACKET expr RIGHT_SBRACKET
  private static boolean unary_expr_1_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_1_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_SBRACKET);
    r = r && expr(b, l + 1);
    r = r && consumeToken(b, RIGHT_SBRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  // immutable_postfix_expr_suffix?
  private static boolean unary_expr_1_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expr_1_3")) return false;
    immutable_postfix_expr_suffix(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID)
  //                     (LEFT_SBRACKET RIGHT_SBRACKET)? var_name ASSIGN expr
  public static boolean var_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_declaration")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VAR_DECLARATION, "<var declaration>");
    r = var_declaration_0(b, l + 1);
    r = r && var_declaration_1(b, l + 1);
    r = r && var_name(b, l + 1);
    r = r && consumeToken(b, ASSIGN);
    r = r && expr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ANY_TYPE|INT_TYPE|DOUBLE_TYPE|STRING_TYPE|BOOLEAN_TYPE|COLLECTION_TYPE|SONG_TYPE|WEIGHTS_KEYWORD|ALBUM_TYPE|JAVA_TYPE|ID
  private static boolean var_declaration_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_declaration_0")) return false;
    boolean r;
    r = consumeToken(b, ANY_TYPE);
    if (!r) r = consumeToken(b, INT_TYPE);
    if (!r) r = consumeToken(b, DOUBLE_TYPE);
    if (!r) r = consumeToken(b, STRING_TYPE);
    if (!r) r = consumeToken(b, BOOLEAN_TYPE);
    if (!r) r = consumeToken(b, COLLECTION_TYPE);
    if (!r) r = consumeToken(b, SONG_TYPE);
    if (!r) r = consumeToken(b, WEIGHTS_KEYWORD);
    if (!r) r = consumeToken(b, ALBUM_TYPE);
    if (!r) r = consumeToken(b, JAVA_TYPE);
    if (!r) r = consumeToken(b, ID);
    return r;
  }

  // (LEFT_SBRACKET RIGHT_SBRACKET)?
  private static boolean var_declaration_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_declaration_1")) return false;
    var_declaration_1_0(b, l + 1);
    return true;
  }

  // LEFT_SBRACKET RIGHT_SBRACKET
  private static boolean var_declaration_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_declaration_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LEFT_SBRACKET, RIGHT_SBRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ID
  public static boolean var_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "var_name")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, VAR_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // INT WEIGHT_UNIT
  public static boolean weight_amount(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "weight_amount")) return false;
    if (!nextTokenIs(b, INT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, INT, WEIGHT_UNIT);
    exit_section_(b, m, WEIGHT_AMOUNT, r);
    return r;
  }

  /* ********************************************************** */
  // WEIGHTS_KEYWORD LEFT_SBRACKET (ID | function_call) RIGHT_SBRACKET
  public static boolean weights_define(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "weights_define")) return false;
    if (!nextTokenIs(b, WEIGHTS_KEYWORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, WEIGHTS_KEYWORD, LEFT_SBRACKET);
    r = r && weights_define_2(b, l + 1);
    r = r && consumeToken(b, RIGHT_SBRACKET);
    exit_section_(b, m, WEIGHTS_DEFINE, r);
    return r;
  }

  // ID | function_call
  private static boolean weights_define_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "weights_define_2")) return false;
    boolean r;
    r = consumeToken(b, ID);
    if (!r) r = function_call(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // single_weight+
  public static boolean weights_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "weights_expr")) return false;
    if (!nextTokenIs(b, WEIGHT_PIPE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = single_weight(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!single_weight(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "weights_expr", c)) break;
    }
    exit_section_(b, m, WEIGHTS_EXPR, r);
    return r;
  }

}
