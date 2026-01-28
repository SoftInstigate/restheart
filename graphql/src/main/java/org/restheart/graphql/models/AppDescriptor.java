/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql.models;

public class AppDescriptor {
    private Boolean enabled;
    private String description;
    private String uri;

    private AppDescriptor(Boolean enabled, String description, String uri) {
        this.enabled = enabled;
        this.description = description;
        this.uri = uri;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUri() {
        return uri;
    }

    public void setUrl(String uri) {
        this.uri = uri;
    }

    public static class Builder {

        private Boolean enabled;
        private String description;
        private String uri;

        private Builder() {
        }

        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public AppDescriptor build() {
            return new AppDescriptor(this.enabled, this.description, this.uri);
        }
    }
}
