package no.ssb.vtl.model;

import java.util.function.Function;

/**
 * Base VTL expression
 *
 * Represents a scalar value (VTLObject)  whose computation can be function of a data point (ultimately of a Bindings,
 * but we are not there yet) but still exposes its type.
 *
 */
public abstract class VTLExpression implements Function<DataPoint, VTLObject> {
    
    public abstract Class<?> getType();
    
    @Override
    public String toString() {
        return "VTLExpression";
    }
    
    
    public static class Builder {
        private final Function<DataPoint, VTLObject> function;
        private final Class<?> type;
        private String description;
    
        public Builder(Class<?> type, Function<DataPoint, VTLObject> function) {
            this.function = function;
            this.type = type;
        }
    
        public Builder description(String description) {
            this.description = description;
            return this;
        }
    
        public VTLExpression build() {
            return new VTLExpression() {
                @Override
                public VTLObject apply(DataPoint dataPoint) {
                    return function.apply(dataPoint);
                }
        
                @Override
                public Class<?> getType() {
                    return type;
                }
        
                @Override
                public String toString() {
                    return description;
                }
            };
        }
    }
}