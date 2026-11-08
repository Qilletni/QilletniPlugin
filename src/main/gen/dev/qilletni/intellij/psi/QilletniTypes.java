// This is a generated file. Not intended for manual editing.
package dev.qilletni.intellij.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import dev.qilletni.intellij.psi.impl.*;

public interface QilletniTypes {

  IElementType ADDITIVE_EXPR = QilletniElementTypeFactory.createElementType("ADDITIVE_EXPR");
  IElementType ALBUM_EXPR = QilletniElementTypeFactory.createElementType("ALBUM_EXPR");
  IElementType ALBUM_URL_OR_NAME_PAIR = QilletniElementTypeFactory.createElementType("ALBUM_URL_OR_NAME_PAIR");
  IElementType ASMT = QilletniElementTypeFactory.createElementType("ASMT");
  IElementType ASMT_BASE = QilletniElementTypeFactory.createElementType("ASMT_BASE");
  IElementType BODY = QilletniElementTypeFactory.createElementType("BODY");
  IElementType BODY_STMT = QilletniElementTypeFactory.createElementType("BODY_STMT");
  IElementType COLLECTION_EXPR = QilletniElementTypeFactory.createElementType("COLLECTION_EXPR");
  IElementType COLLECTION_LIMIT = QilletniElementTypeFactory.createElementType("COLLECTION_LIMIT");
  IElementType COLLECTION_URL_OR_NAME_PAIR = QilletniElementTypeFactory.createElementType("COLLECTION_URL_OR_NAME_PAIR");
  IElementType CONSTRUCTOR_NAME = QilletniElementTypeFactory.createElementType("CONSTRUCTOR_NAME");
  IElementType DOUBLE_EXPR = QilletniElementTypeFactory.createElementType("DOUBLE_EXPR");
  IElementType ELSEIF_LIST = QilletniElementTypeFactory.createElementType("ELSEIF_LIST");
  IElementType ELSE_BODY = QilletniElementTypeFactory.createElementType("ELSE_BODY");
  IElementType ENTITY_BODY = QilletniElementTypeFactory.createElementType("ENTITY_BODY");
  IElementType ENTITY_CONSTRUCTOR = QilletniElementTypeFactory.createElementType("ENTITY_CONSTRUCTOR");
  IElementType ENTITY_DEF = QilletniElementTypeFactory.createElementType("ENTITY_DEF");
  IElementType ENTITY_INITIALIZE = QilletniElementTypeFactory.createElementType("ENTITY_INITIALIZE");
  IElementType ENTITY_NAME = QilletniElementTypeFactory.createElementType("ENTITY_NAME");
  IElementType ENTITY_PROPERTY_DECLARATION = QilletniElementTypeFactory.createElementType("ENTITY_PROPERTY_DECLARATION");
  IElementType EXPR = QilletniElementTypeFactory.createElementType("EXPR");
  IElementType EXPR_LIST = QilletniElementTypeFactory.createElementType("EXPR_LIST");
  IElementType FOREACH_RANGE = QilletniElementTypeFactory.createElementType("FOREACH_RANGE");
  IElementType FOR_EXPR = QilletniElementTypeFactory.createElementType("FOR_EXPR");
  IElementType FOR_STMT = QilletniElementTypeFactory.createElementType("FOR_STMT");
  IElementType FUNCTION_CALL = QilletniElementTypeFactory.createElementType("FUNCTION_CALL");
  IElementType FUNCTION_DEFINITION = QilletniElementTypeFactory.createElementType("FUNCTION_DEFINITION");
  IElementType FUNCTION_DEF_PARAMS = QilletniElementTypeFactory.createElementType("FUNCTION_DEF_PARAMS");
  IElementType FUNCTION_NAME = QilletniElementTypeFactory.createElementType("FUNCTION_NAME");
  IElementType FUNCTION_ON_TYPE = QilletniElementTypeFactory.createElementType("FUNCTION_ON_TYPE");
  IElementType IF_STMT = QilletniElementTypeFactory.createElementType("IF_STMT");
  IElementType IMMUTABLE_POSTFIX_EXPR_SUFFIX = QilletniElementTypeFactory.createElementType("IMMUTABLE_POSTFIX_EXPR_SUFFIX");
  IElementType IMPORT_FILE = QilletniElementTypeFactory.createElementType("IMPORT_FILE");
  IElementType INT_EXPR = QilletniElementTypeFactory.createElementType("INT_EXPR");
  IElementType IS_EXPR = QilletniElementTypeFactory.createElementType("IS_EXPR");
  IElementType JAVA_EXPR = QilletniElementTypeFactory.createElementType("JAVA_EXPR");
  IElementType LHS_CORE = QilletniElementTypeFactory.createElementType("LHS_CORE");
  IElementType LHS_MEMBER = QilletniElementTypeFactory.createElementType("LHS_MEMBER");
  IElementType LIMIT_AMOUNT = QilletniElementTypeFactory.createElementType("LIMIT_AMOUNT");
  IElementType LIST_EXPRESSION = QilletniElementTypeFactory.createElementType("LIST_EXPRESSION");
  IElementType LOGICAL_AND_EXPR = QilletniElementTypeFactory.createElementType("LOGICAL_AND_EXPR");
  IElementType LOGICAL_OR_EXPR = QilletniElementTypeFactory.createElementType("LOGICAL_OR_EXPR");
  IElementType MULTIPLICATIVE_EXPR = QilletniElementTypeFactory.createElementType("MULTIPLICATIVE_EXPR");
  IElementType ORDER_DEFINE = QilletniElementTypeFactory.createElementType("ORDER_DEFINE");
  IElementType ORDER_SETTINGS = QilletniElementTypeFactory.createElementType("ORDER_SETTINGS");
  IElementType PARAM_NAME = QilletniElementTypeFactory.createElementType("PARAM_NAME");
  IElementType PLAY_STMT = QilletniElementTypeFactory.createElementType("PLAY_STMT");
  IElementType POSTFIX_EXPR = QilletniElementTypeFactory.createElementType("POSTFIX_EXPR");
  IElementType POSTFIX_SUFFIX = QilletniElementTypeFactory.createElementType("POSTFIX_SUFFIX");
  IElementType PRIMARY_EXPR = QilletniElementTypeFactory.createElementType("PRIMARY_EXPR");
  IElementType PROG = QilletniElementTypeFactory.createElementType("PROG");
  IElementType PROPERTY_NAME = QilletniElementTypeFactory.createElementType("PROPERTY_NAME");
  IElementType PROVIDER_STMT = QilletniElementTypeFactory.createElementType("PROVIDER_STMT");
  IElementType RANGE_EXPR = QilletniElementTypeFactory.createElementType("RANGE_EXPR");
  IElementType RELATIONAL_EXPR = QilletniElementTypeFactory.createElementType("RELATIONAL_EXPR");
  IElementType RETURN_STMT = QilletniElementTypeFactory.createElementType("RETURN_STMT");
  IElementType RUNNING = QilletniElementTypeFactory.createElementType("RUNNING");
  IElementType SINGLE_WEIGHT = QilletniElementTypeFactory.createElementType("SINGLE_WEIGHT");
  IElementType SONG_EXPR = QilletniElementTypeFactory.createElementType("SONG_EXPR");
  IElementType SONG_URL_OR_NAME_PAIR = QilletniElementTypeFactory.createElementType("SONG_URL_OR_NAME_PAIR");
  IElementType STMT = QilletniElementTypeFactory.createElementType("STMT");
  IElementType STR_EXPR = QilletniElementTypeFactory.createElementType("STR_EXPR");
  IElementType UNARY_EXPR = QilletniElementTypeFactory.createElementType("UNARY_EXPR");
  IElementType VAR_DECLARATION = QilletniElementTypeFactory.createElementType("VAR_DECLARATION");
  IElementType VAR_NAME = QilletniElementTypeFactory.createElementType("VAR_NAME");
  IElementType WEIGHTS_DEFINE = QilletniElementTypeFactory.createElementType("WEIGHTS_DEFINE");
  IElementType WEIGHTS_EXPR = QilletniElementTypeFactory.createElementType("WEIGHTS_EXPR");
  IElementType WEIGHT_AMOUNT = QilletniElementTypeFactory.createElementType("WEIGHT_AMOUNT");

  IElementType ALBUM_TYPE = new QilletniTokenType("ALBUM_TYPE");
  IElementType ANDAND = new QilletniTokenType("ANDAND");
  IElementType ANY_TYPE = new QilletniTokenType("ANY_TYPE");
  IElementType AS = new QilletniTokenType("AS");
  IElementType ASSIGN = new QilletniTokenType("ASSIGN");
  IElementType BLOCK_COMMENT = new QilletniTokenType("BLOCK_COMMENT");
  IElementType BOOL = new QilletniTokenType("BOOL");
  IElementType BOOLEAN_TYPE = new QilletniTokenType("BOOLEAN_TYPE");
  IElementType BY = new QilletniTokenType("BY");
  IElementType COLLECTION_ORDER = new QilletniTokenType("COLLECTION_ORDER");
  IElementType COLLECTION_TYPE = new QilletniTokenType("COLLECTION_TYPE");
  IElementType COLON = new QilletniTokenType("COLON");
  IElementType COMMA = new QilletniTokenType("COMMA");
  IElementType DECREMENT = new QilletniTokenType("DECREMENT");
  IElementType DIV = new QilletniTokenType("DIV");
  IElementType DOC_COMMENT = new QilletniTokenType("DOC_COMMENT");
  IElementType DOT = new QilletniTokenType("DOT");
  IElementType DOUBLE = new QilletniTokenType("DOUBLE");
  IElementType DOUBLE_DOT = new QilletniTokenType("DOUBLE_DOT");
  IElementType DOUBLE_TYPE = new QilletniTokenType("DOUBLE_TYPE");
  IElementType ELSE_KEYWORD = new QilletniTokenType("ELSE_KEYWORD");
  IElementType EMPTY = new QilletniTokenType("EMPTY");
  IElementType ENTITY = new QilletniTokenType("ENTITY");
  IElementType FLOOR_DIV = new QilletniTokenType("FLOOR_DIV");
  IElementType FOR_KEYWORD = new QilletniTokenType("FOR_KEYWORD");
  IElementType FUNCTION_DEF = new QilletniTokenType("FUNCTION_DEF");
  IElementType ID = new QilletniTokenType("ID");
  IElementType IF_KEYWORD = new QilletniTokenType("IF_KEYWORD");
  IElementType IMPORT = new QilletniTokenType("IMPORT");
  IElementType INCREMENT = new QilletniTokenType("INCREMENT");
  IElementType INT = new QilletniTokenType("INT");
  IElementType INT_TYPE = new QilletniTokenType("INT_TYPE");
  IElementType IS_KEYWORD = new QilletniTokenType("IS_KEYWORD");
  IElementType JAVA_TYPE = new QilletniTokenType("JAVA_TYPE");
  IElementType LEFT_CBRACKET = new QilletniTokenType("LEFT_CBRACKET");
  IElementType LEFT_PAREN = new QilletniTokenType("LEFT_PAREN");
  IElementType LEFT_SBRACKET = new QilletniTokenType("LEFT_SBRACKET");
  IElementType LIMIT_PARAM = new QilletniTokenType("LIMIT_PARAM");
  IElementType LIMIT_UNIT = new QilletniTokenType("LIMIT_UNIT");
  IElementType LINE_COMMENT = new QilletniTokenType("LINE_COMMENT");
  IElementType LOOP_PARAM = new QilletniTokenType("LOOP_PARAM");
  IElementType MINUS = new QilletniTokenType("MINUS");
  IElementType MINUS_EQUALS = new QilletniTokenType("MINUS_EQUALS");
  IElementType MOD = new QilletniTokenType("MOD");
  IElementType NATIVE = new QilletniTokenType("NATIVE");
  IElementType NEW = new QilletniTokenType("NEW");
  IElementType NOT = new QilletniTokenType("NOT");
  IElementType ON = new QilletniTokenType("ON");
  IElementType ORDER_PARAM = new QilletniTokenType("ORDER_PARAM");
  IElementType OROR = new QilletniTokenType("OROR");
  IElementType PLAY = new QilletniTokenType("PLAY");
  IElementType PLUS = new QilletniTokenType("PLUS");
  IElementType PLUS_EQUALS = new QilletniTokenType("PLUS_EQUALS");
  IElementType PROVIDER = new QilletniTokenType("PROVIDER");
  IElementType RANGE_INFINITY = new QilletniTokenType("RANGE_INFINITY");
  IElementType REL_OP = new QilletniTokenType("REL_OP");
  IElementType RETURN = new QilletniTokenType("RETURN");
  IElementType RIGHT_CBRACKET = new QilletniTokenType("RIGHT_CBRACKET");
  IElementType RIGHT_PAREN = new QilletniTokenType("RIGHT_PAREN");
  IElementType RIGHT_SBRACKET = new QilletniTokenType("RIGHT_SBRACKET");
  IElementType SONG_TYPE = new QilletniTokenType("SONG_TYPE");
  IElementType STAR = new QilletniTokenType("STAR");
  IElementType STATIC = new QilletniTokenType("STATIC");
  IElementType STRING = new QilletniTokenType("STRING");
  IElementType STRING_TYPE = new QilletniTokenType("STRING_TYPE");
  IElementType WEIGHTS_KEYWORD = new QilletniTokenType("WEIGHTS_KEYWORD");
  IElementType WEIGHT_PIPE = new QilletniTokenType("WEIGHT_PIPE");
  IElementType WEIGHT_UNIT = new QilletniTokenType("WEIGHT_UNIT");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ADDITIVE_EXPR) {
        return new QilletniAdditiveExprImpl(node);
      }
      else if (type == ALBUM_EXPR) {
        return new QilletniAlbumExprImpl(node);
      }
      else if (type == ALBUM_URL_OR_NAME_PAIR) {
        return new QilletniAlbumUrlOrNamePairImpl(node);
      }
      else if (type == ASMT) {
        return new QilletniAsmtImpl(node);
      }
      else if (type == ASMT_BASE) {
        return new QilletniAsmtBaseImpl(node);
      }
      else if (type == BODY) {
        return new QilletniBodyImpl(node);
      }
      else if (type == BODY_STMT) {
        return new QilletniBodyStmtImpl(node);
      }
      else if (type == COLLECTION_EXPR) {
        return new QilletniCollectionExprImpl(node);
      }
      else if (type == COLLECTION_LIMIT) {
        return new QilletniCollectionLimitImpl(node);
      }
      else if (type == COLLECTION_URL_OR_NAME_PAIR) {
        return new QilletniCollectionUrlOrNamePairImpl(node);
      }
      else if (type == CONSTRUCTOR_NAME) {
        return new QilletniConstructorNameImpl(node);
      }
      else if (type == DOUBLE_EXPR) {
        return new QilletniDoubleExprImpl(node);
      }
      else if (type == ELSEIF_LIST) {
        return new QilletniElseifListImpl(node);
      }
      else if (type == ELSE_BODY) {
        return new QilletniElseBodyImpl(node);
      }
      else if (type == ENTITY_BODY) {
        return new QilletniEntityBodyImpl(node);
      }
      else if (type == ENTITY_CONSTRUCTOR) {
        return new QilletniEntityConstructorImpl(node);
      }
      else if (type == ENTITY_DEF) {
        return new QilletniEntityDefImpl(node);
      }
      else if (type == ENTITY_INITIALIZE) {
        return new QilletniEntityInitializeImpl(node);
      }
      else if (type == ENTITY_NAME) {
        return new QilletniEntityNameImpl(node);
      }
      else if (type == ENTITY_PROPERTY_DECLARATION) {
        return new QilletniEntityPropertyDeclarationImpl(node);
      }
      else if (type == EXPR) {
        return new QilletniExprImpl(node);
      }
      else if (type == EXPR_LIST) {
        return new QilletniExprListImpl(node);
      }
      else if (type == FOREACH_RANGE) {
        return new QilletniForeachRangeImpl(node);
      }
      else if (type == FOR_EXPR) {
        return new QilletniForExprImpl(node);
      }
      else if (type == FOR_STMT) {
        return new QilletniForStmtImpl(node);
      }
      else if (type == FUNCTION_CALL) {
        return new QilletniFunctionCallImpl(node);
      }
      else if (type == FUNCTION_DEFINITION) {
        return new QilletniFunctionDefImpl(node);
      }
      else if (type == FUNCTION_DEF_PARAMS) {
        return new QilletniFunctionDefParamsImpl(node);
      }
      else if (type == FUNCTION_NAME) {
        return new QilletniFunctionNameImpl(node);
      }
      else if (type == FUNCTION_ON_TYPE) {
        return new QilletniFunctionOnTypeImpl(node);
      }
      else if (type == IF_STMT) {
        return new QilletniIfStmtImpl(node);
      }
      else if (type == IMMUTABLE_POSTFIX_EXPR_SUFFIX) {
        return new QilletniImmutablePostfixExprSuffixImpl(node);
      }
      else if (type == IMPORT_FILE) {
        return new QilletniImportFileImpl(node);
      }
      else if (type == INT_EXPR) {
        return new QilletniIntExprImpl(node);
      }
      else if (type == IS_EXPR) {
        return new QilletniIsExprImpl(node);
      }
      else if (type == JAVA_EXPR) {
        return new QilletniJavaExprImpl(node);
      }
      else if (type == LHS_CORE) {
        return new QilletniLhsCoreImpl(node);
      }
      else if (type == LHS_MEMBER) {
        return new QilletniLhsMemberImpl(node);
      }
      else if (type == LIMIT_AMOUNT) {
        return new QilletniLimitAmountImpl(node);
      }
      else if (type == LIST_EXPRESSION) {
        return new QilletniListExpressionImpl(node);
      }
      else if (type == LOGICAL_AND_EXPR) {
        return new QilletniLogicalAndExprImpl(node);
      }
      else if (type == LOGICAL_OR_EXPR) {
        return new QilletniLogicalOrExprImpl(node);
      }
      else if (type == MULTIPLICATIVE_EXPR) {
        return new QilletniMultiplicativeExprImpl(node);
      }
      else if (type == ORDER_DEFINE) {
        return new QilletniOrderDefineImpl(node);
      }
      else if (type == ORDER_SETTINGS) {
        return new QilletniOrderSettingsImpl(node);
      }
      else if (type == PARAM_NAME) {
        return new QilletniParamNameImpl(node);
      }
      else if (type == PLAY_STMT) {
        return new QilletniPlayStmtImpl(node);
      }
      else if (type == POSTFIX_EXPR) {
        return new QilletniPostfixExprImpl(node);
      }
      else if (type == POSTFIX_SUFFIX) {
        return new QilletniPostfixSuffixImpl(node);
      }
      else if (type == PRIMARY_EXPR) {
        return new QilletniPrimaryExprImpl(node);
      }
      else if (type == PROG) {
        return new QilletniProgImpl(node);
      }
      else if (type == PROPERTY_NAME) {
        return new QilletniPropertyNameImpl(node);
      }
      else if (type == PROVIDER_STMT) {
        return new QilletniProviderStmtImpl(node);
      }
      else if (type == RANGE_EXPR) {
        return new QilletniRangeExprImpl(node);
      }
      else if (type == RELATIONAL_EXPR) {
        return new QilletniRelationalExprImpl(node);
      }
      else if (type == RETURN_STMT) {
        return new QilletniReturnStmtImpl(node);
      }
      else if (type == RUNNING) {
        return new QilletniRunningImpl(node);
      }
      else if (type == SINGLE_WEIGHT) {
        return new QilletniSingleWeightImpl(node);
      }
      else if (type == SONG_EXPR) {
        return new QilletniSongExprImpl(node);
      }
      else if (type == SONG_URL_OR_NAME_PAIR) {
        return new QilletniSongUrlOrNamePairImpl(node);
      }
      else if (type == STMT) {
        return new QilletniStmtImpl(node);
      }
      else if (type == STR_EXPR) {
        return new QilletniStrExprImpl(node);
      }
      else if (type == UNARY_EXPR) {
        return new QilletniUnaryExprImpl(node);
      }
      else if (type == VAR_DECLARATION) {
        return new QilletniVarDeclarationImpl(node);
      }
      else if (type == VAR_NAME) {
        return new QilletniVarNameImpl(node);
      }
      else if (type == WEIGHTS_DEFINE) {
        return new QilletniWeightsDefineImpl(node);
      }
      else if (type == WEIGHTS_EXPR) {
        return new QilletniWeightsExprImpl(node);
      }
      else if (type == WEIGHT_AMOUNT) {
        return new QilletniWeightAmountImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
