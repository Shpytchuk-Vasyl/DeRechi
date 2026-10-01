package org.shpytchuk.clientapi.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

public class SpatialFunctionContributor implements FunctionContributor {

    private static final String DWITHIN_FUNCTION = "dwithin";

    private static final String DWITHIN_PATTERN = "st_dwithin(?1, CAST(?2 AS geography), ?3)";

    @Override
    public void contributeFunctions(FunctionContributions functions) {
        functions.getFunctionRegistry().registerPattern(
                DWITHIN_FUNCTION,
                DWITHIN_PATTERN,
                functions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.BOOLEAN));
    }

    @Override
    public int ordinal() {
        return 1001;
    }
}
