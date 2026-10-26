package org.shpytchuk.clientapi.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;
import org.shpytchuk.clientapi.language.SearchLanguage;

import java.util.stream.Collectors;

public class FullTextFunctionContributor implements FunctionContributor {

    public static final String MATCHES_WORDS_FUNCTION = "matches_words";

    private static final String WITH_UNIFIED_APOSTROPHES = "translate(%s, '''’', 'ʼʼ')";

    private static final String REGCONFIG_OF_LANGUAGE = "CAST(%s AS regconfig)";

    private static final String MATCHES_WORDS_TEMPLATE = "to_tsvector(%1$s, %2$s) @@ plainto_tsquery(%1$s, %3$s)";

    @Override
    public void contributeFunctions(FunctionContributions functions) {
        var booleanType = functions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN);

        String config = REGCONFIG_OF_LANGUAGE.formatted(regconfigByIsoCode("?1"));
        String document = WITH_UNIFIED_APOSTROPHES.formatted("?2 || ' ' || coalesce(?3, '')");
        String search = WITH_UNIFIED_APOSTROPHES.formatted("?4");

        functions.getFunctionRegistry().registerPattern(
                MATCHES_WORDS_FUNCTION,
                MATCHES_WORDS_TEMPLATE.formatted(config, document, search),
                booleanType);
    }

    private static String regconfigByIsoCode(String isoCode) {
        return SearchLanguage.detectable().stream()
                .map(language -> "WHEN '%s' THEN '%s'".formatted(language.isoCode(), language.regconfig()))
                .collect(Collectors.joining(" ", "CASE " + isoCode + " ",
                        " ELSE '" + SearchLanguage.SIMPLE.regconfig() + "' END"));
    }

    @Override
    public int ordinal() {
        return 1001;
    }
}
