package org.shpytchuk.worker.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;
import org.shpytchuk.worker.language.SearchLanguage;

public class FullTextFunctionContributor implements FunctionContributor {

    private static final String PATTERN_TEMPLATE =
            "ts_rank(to_tsvector('%1$s', ?1), plainto_tsquery('%1$s', ?2))";

    public static final String RANK_FUNCTION = "ts_rank_cfg";

    private static final String RANK_PATTERN =
            "ts_rank(to_tsvector(CAST(?1 AS regconfig), ?2), plainto_tsquery(CAST(?1 AS regconfig), ?3))";

    public static final String MATCH_RANK_FUNCTION = "match_rank_cfg";

    private static final String MATCH_RANK_TEMPLATE =
            "ts_rank_cd(to_tsvector(%1$s, %2$s), CAST(replace(CAST(plainto_tsquery(%1$s, %3$s) AS text), '&', '|') AS tsquery))";

    private static final int MIN_SHARED_WORDS = 2;

    private static final String SHARES_WORDS_TEMPLATE = """
            (SELECT count(*) FILTER (WHERE word = ANY (tsvector_to_array(to_tsvector('%1$s', ?1))))
                    >= greatest(1, least(%2$d, count(*)))
             FROM unnest(tsvector_to_array(to_tsvector('%1$s', ?2))) word)""";

    private static final String DWITHIN_FUNCTION = "dwithin";

    private static final String DWITHIN_PATTERN = "st_dwithin(?1, CAST(?2 AS geography), ?3)";

    @Override
    public void contributeFunctions(FunctionContributions functions) {
        var returnType = functions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.DOUBLE);
        var booleanType = functions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN);

        functions.getFunctionRegistry().registerPattern(
                DWITHIN_FUNCTION,
                DWITHIN_PATTERN,
                booleanType);

        for (SearchLanguage language : SearchLanguage.values()) {
            functions.getFunctionRegistry().registerPattern(
                    language.rankFunction(),
                    PATTERN_TEMPLATE.formatted(language.regconfig()),
                    returnType);
            functions.getFunctionRegistry().registerPattern(
                    language.matchRankFunction(),
                    MATCH_RANK_TEMPLATE.formatted("'" + language.regconfig() + "'", "?1", "?2"),
                    returnType);
            functions.getFunctionRegistry().registerPattern(
                    language.sharesWordsFunction(),
                    SHARES_WORDS_TEMPLATE.formatted(language.regconfig(), MIN_SHARED_WORDS),
                    booleanType);
        }

        functions.getFunctionRegistry().registerPattern(RANK_FUNCTION, RANK_PATTERN, returnType);
        functions.getFunctionRegistry().registerPattern(
                MATCH_RANK_FUNCTION,
                MATCH_RANK_TEMPLATE.formatted("CAST(?1 AS regconfig)", "?2", "?3"),
                returnType);
    }

    @Override
    public int ordinal() {
        return 1001;
    }
}
