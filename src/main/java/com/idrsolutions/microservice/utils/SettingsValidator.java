package com.idrsolutions.microservice.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A generic class used to validate the settings that will be passed into a conversion.
 * SettingsValidator will notify you if unexpected parameters are passed,
 * or if required parameters are missing.
 */
public class SettingsValidator {

    private final StringBuilder errorMessage = new StringBuilder();
    private final HashMap<String, String> paramMap = new HashMap<>();

    public SettingsValidator(final Map<String, String> settings) {
        if (settings != null) {
            paramMap.putAll(settings);
        }
    }

    public String validateString(final String setting, final String[] values, final boolean required) {
        if (paramMap.containsKey(setting)) {
            final String value = paramMap.remove(setting);
            if (!Arrays.asList(values).contains(value)) {
                errorMessage.append(required ? "Required " : "Optional ").append("setting \"").append(setting)
                        .append("\" has incorrect value. Valid values are ").append(Arrays.toString(values)).append('\n');
            }
            return value;
        } else {
            if (required) {
                errorMessage.append("Required setting \"").append(setting).append("\" missing. Valid values are ")
                        .append(Arrays.toString(values)).append(".\n");
            }
        }
        return null;
    }

    public String validateString(final String setting, final String regex, final boolean required) {
        if (paramMap.containsKey(setting)) {
            final String value = paramMap.remove(setting);
            if (!value.matches(regex)) {
                errorMessage.append(required ? "Required " : "Optional ").append("setting \"").append(setting)
                        .append("\" has incorrect value. Please check the API documents for more details.\n");
            }
            return value;
        } else {
            if (required) {
                errorMessage.append("Required setting \"").append(setting).append("\" missing. Valid value must match the regex \"")
                        .append(regex).append("\".\n");
            }
        }
        return null;
    }

    public Boolean validateBoolean(final String setting, final boolean required) {
        if (paramMap.containsKey(setting)) {
            final String value = paramMap.remove(setting);
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                errorMessage.append(required ? "Required " : "Optional ").append("setting \"").append(setting)
                        .append("\" has incorrect value. Valid values are true or false.\n");
            } else {
                return Boolean.parseBoolean(value);
            }
        } else {
            if (required) {
                errorMessage.append("Required setting \"").append(setting).append("\" missing. Valid values are true or false.\n");
            }
        }
        return null;
    }

    public Float validateFloat(final String setting, final float[] range, final boolean required) {
        if (paramMap.containsKey(setting)) {
            final String value = paramMap.remove(setting);

            if (!value.isEmpty()) {
                final float fValue;
                if (value.matches("-?\\d+\\.?(\\d+)?")) {
                    fValue = Float.parseFloat(value);
                } else {
                    fValue = Float.NaN;
                }
                if (Float.isNaN(fValue) || (fValue < range[0] || range[1] < fValue)) {
                    errorMessage.append(required ? "Required " : "Optional ").append("setting \"").append(setting)
                            .append("\" has incorrect value. Valid values are between ").append(range[0]).append(" and ")
                            .append(range[1]).append(".\n");
                }
                return fValue;
            } else {
                if (required) {
                    errorMessage.append("Required setting \"").append(setting).append("\" should be a float number and not ").append(value).append(" Valid values are between ")
                            .append(range[0]).append(" and ").append(range[1]).append(".\n");
                }
                return Float.NaN;
            }
        } else {
            if (required) {
                errorMessage.append("Required setting \"").append(setting).append("\" missing. Valid values are between ")
                        .append(range[0]).append(" and ").append(range[1]).append(".\n");
            }
        }
        return null;
    }

    public boolean isValid() {
        if (!paramMap.isEmpty()) {
            errorMessage.append("The following settings were not recognised.\n");
            final Set<String> keys = paramMap.keySet();
            for (String key : keys) {
                errorMessage.append("    ").append(key).append('\n');
            }
            paramMap.clear();
        }

        return errorMessage.length() == 0;
    }

    public String getMessage() {
        return errorMessage.toString();
    }

}
