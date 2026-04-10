package com.adags.hospital.dto.lab;

import java.util.List;

public class AnalyzerResultDto {

    private final List<ParameterRow> parameters;

    public AnalyzerResultDto(List<ParameterRow> parameters) {
        this.parameters = parameters;
    }

    public List<ParameterRow> getParameters() {
        return parameters;
    }

    public static class ParameterRow {
        private final String code;
        private final String name;
        private final String value;
        private final String unit;
        private final String refRange;
        private final String flag;

        public ParameterRow(String code, String name, String value,
                            String unit, String refRange, String flag) {
            this.code     = code;
            this.name     = name;
            this.value    = value;
            this.unit     = unit;
            this.refRange = refRange;
            this.flag     = flag;
        }

        public String getCode()     { return code;     }
        public String getName()     { return name;     }
        public String getValue()    { return value;    }
        public String getUnit()     { return unit;     }
        public String getRefRange() { return refRange; }
        public String getFlag()     { return flag;     }
    }
}
