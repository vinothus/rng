package com.hid.pqc.rng.model;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BraketProgram {
    @JsonProperty("braketSchemaHeader")
    private BraketSchemaHeader braketSchemaHeader;

    @JsonProperty("source")
    private String source;

    // Getters and Setters
    public BraketSchemaHeader getBraketSchemaHeader() {
        return braketSchemaHeader;
    }

    public void setBraketSchemaHeader(BraketSchemaHeader braketSchemaHeader) {
        this.braketSchemaHeader = braketSchemaHeader;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    // Inner class for braketSchemaHeader
    public static class BraketSchemaHeader {
        @JsonProperty("name")
        private String name;

        @JsonProperty("version")
        private String version;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
