package com.cisco.dsb.common.util.log;

import gov.nist.core.GenericObject;
import gov.nist.core.GenericObjectList;
import gov.nist.core.NameValue;

public privileged aspect PrivilegedObfuscationAspect {

    StringBuilder around(NameValue nameValue, StringBuilder buffer) :
     execution(public java.lang.StringBuilder gov.nist.core.NameValue.encode(java.lang.StringBuilder)) && args(buffer) && target(nameValue) {
        if (ObfuscationAspect.isObfuscationEnabledForThisThread()) {
            boolean isFlagParameter = nameValue.isFlagParameter;
            String separator = nameValue.separator;
            String quotes = nameValue.quotes;

            String name = nameValue.name;
            Object value = nameValue.value;

            boolean obfuscateValue = LogUtils.isObfuscatedParam(name);

            if (obfuscateValue) {
                if (name != null && value != null && !isFlagParameter) {
                    if (!GenericObject.isMySubclass(value.getClass()) &&
                            !GenericObjectList.isMySubclass(value.getClass()) &&
                            value.toString().length() != 0) {
                        String obfuscatedValue = LogUtils.obfuscateEntireString(value.toString());
                        buffer.append(name).append(separator).append(quotes).append(obfuscatedValue).append(quotes);
                        return buffer;
                    }
                } else if (name == null && value != null) {
                    if (!GenericObject.isMySubclass(value.getClass()) &&
                            !GenericObjectList.isMySubclass(value.getClass())) {
                        String obfuscatedValue = LogUtils.obfuscateEntireString(value.toString());
                        buffer.append(quotes).append(obfuscatedValue).append(quotes);
                        return buffer;
                    }
                }
            }
        }
        return proceed(nameValue, buffer);
    }

}
