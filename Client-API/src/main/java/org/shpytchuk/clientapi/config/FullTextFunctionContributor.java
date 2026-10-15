package org.shpytchuk.clientapi.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

import java.util.List;

public class FullTextFunctionContributor implements FunctionContributor {

    public static final List<String> SEARCH_CONFIGS = List.of("ukrainian", "polish", "german", "french", "english");

    private static final String MATCHES_WORDS_FUNCTION_PREFIX = "matches_words_";

    private static final String WITH_UNIFIED_APOSTROPHES = "translate(%s, '''’', 'ʼʼ')";

    private static final String MATCHES_WORDS_TEMPLATE = "to_tsvector('%1$s', %2$s) @@ plainto_tsquery('%1$s', %3$s)";

    public static String matchesWordsFunction(String config) {
        return MATCHES_WORDS_FUNCTION_PREFIX + config;
    }

    @Override
    public void contributeFunctions(FunctionContributions functions) {
        var booleanType = functions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN);

        String document = WITH_UNIFIED_APOSTROPHES.formatted("?1 || ' ' || coalesce(?2, '')");
        String search = WITH_UNIFIED_APOSTROPHES.formatted("?3");

        for (String config : SEARCH_CONFIGS) {
            functions.getFunctionRegistry().registerPattern(
                    matchesWordsFunction(config),
                    MATCHES_WORDS_TEMPLATE.formatted(config, document, search),
                    booleanType);
        }
    }

    @Override
    public int ordinal() {
        return 1001;
    }
}
