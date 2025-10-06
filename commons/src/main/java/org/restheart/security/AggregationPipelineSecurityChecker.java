/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security checker for MongoDB aggregation pipelines that validates pipeline stages
 * and operators against configured blacklists to prevent dangerous operations.
 * <p>
 * This class provides comprehensive security validation for aggregation pipelines
 * including stage-level restrictions, operator blacklisting, cross-database operation
 * controls, and JavaScript execution restrictions.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 9.0.0
 */
public class AggregationPipelineSecurityChecker {

  private static final Logger LOGGER = LoggerFactory.getLogger(AggregationPipelineSecurityChecker.class);

  private final boolean enabled;
  private final Set<String> stageBlacklist;
  private final Set<String> operatorBlacklist;
  private final boolean allowCrossDatabaseOperations;
  private final boolean allowJavaScriptExecution;

  /**
   * Security violation details for reporting blocked operations
   */
  public record SecurityViolation(String type, String operator, String location, String reason) {

	@Override
	public String toString() {
	  return String.format("Security violation: %s operator '%s' at %s - %s",
		  type, operator, location, reason);
	}
  }

  /**
   * Creates a new aggregation pipeline security checker with the given configuration.
   *
   * @param config Security configuration map containing:
   *               - enabled: boolean to enable/disable security checks
   *               - stageBlacklist: list of blocked pipeline stage names
   *               - operatorBlacklist: list of blocked operator names
   *               - allowCrossDatabaseOperations: boolean for cross-database access
   *               - allowJavaScriptExecution: boolean for JavaScript execution
   * @throws ConfigurationException if configuration is invalid
   */
  @SuppressWarnings("unchecked")
  public AggregationPipelineSecurityChecker(Map<String, Object> config) {
	if (config == null) {
	  this.enabled = false;
	  this.stageBlacklist = Set.of();
	  this.operatorBlacklist = Set.of();
	  this.allowCrossDatabaseOperations = true;
	  this.allowJavaScriptExecution = true;
	  return;
	}

	this.enabled = (Boolean) config.getOrDefault("enabled", false);

	if (!enabled) {
	  this.stageBlacklist = Set.of();
	  this.operatorBlacklist = Set.of();
	  this.allowCrossDatabaseOperations = true;
	  this.allowJavaScriptExecution = true;
	  return;
	}

	// Parse stage blacklist
	var stageBlacklistConfig = (List<String>) config.get("stageBlacklist");
	if (stageBlacklistConfig != null) {
	  this.stageBlacklist = stageBlacklistConfig.stream()
		  .map(String::toLowerCase)
		  .collect(Collectors.toSet());

	  // Validate that all stage names start with $
	  for (String stage : stageBlacklistConfig) {
		if (!stage.startsWith("$")) {
		  throw new ConfigurationException("All pipeline stage names in stageBlacklist must start with '$': " + stage);
		}
	  }
	} else {
	  this.stageBlacklist = Set.of();
	}

	// Parse operator blacklist
	var operatorBlacklistConfig = (List<String>) config.get("operatorBlacklist");
	if (operatorBlacklistConfig != null) {
	  this.operatorBlacklist = operatorBlacklistConfig.stream()
		  .map(String::toLowerCase)
		  .collect(Collectors.toSet());

	  // Validate that all operator names start with $
	  for (String operator : operatorBlacklistConfig) {
		if (!operator.startsWith("$")) {
		  throw new ConfigurationException("All operator names in operatorBlacklist must start with '$': " + operator);
		}
	  }
	} else {
	  this.operatorBlacklist = Set.of();
	}

	this.allowCrossDatabaseOperations = (Boolean) config.getOrDefault("allowCrossDatabaseOperations", false);
	this.allowJavaScriptExecution = (Boolean) config.getOrDefault("allowJavaScriptExecution", false);

	LOGGER.info("AggregationPipelineSecurityChecker initialized: enabled={}, stageBlacklist={}, operatorBlacklist={}",
		enabled, stageBlacklist, operatorBlacklist);
  }

  /**
   * Validates an aggregation pipeline against security policies.
   *
   * @param pipeline        Array of aggregation pipeline stages
   * @param requestDatabase The database name from the request URI (used for cross-database validation)
   * @return Optional containing SecurityViolation if validation fails, empty if validation passes
   */
  public Optional<SecurityViolation> validatePipeline(BsonArray pipeline, String requestDatabase) {
	if (!enabled || pipeline == null) {
	  return Optional.empty();
	}

	LOGGER.debug("Validating aggregation pipeline with {} stages for database '{}'",
		pipeline.size(), requestDatabase);

	for (int i = 0; i < pipeline.size(); i++) {
	  var stage = pipeline.get(i);
	  if (!stage.isDocument()) {
		continue;
	  }

	  var stageDoc = stage.asDocument();
	  var violation = validateStage(stageDoc, requestDatabase, "pipeline[" + i + "]");
	  if (violation.isPresent()) {
		return violation;
	  }
	}

	return Optional.empty();
  }

  /**
   * Validates a single pipeline stage document.
   */
  private Optional<SecurityViolation> validateStage(BsonDocument stage, String requestDatabase, String location) {
	for (String stageOperator : stage.keySet()) {
	  var stageOperatorLower = stageOperator.toLowerCase();

	  // Check for blacklisted stage operators
	  if (stageBlacklist.contains(stageOperatorLower)) {
		return Optional.of(new SecurityViolation(
			"BLACKLISTED_STAGE",
			stageOperator,
			location,
			"Pipeline stage is in security blacklist"
		));
	  }

	  // Special validation for cross-database operations
	  if (!allowCrossDatabaseOperations) {
		var crossDbViolation = validateCrossDatabaseAccess(stageOperator, stage.get(stageOperator),
			requestDatabase, location);
		if (crossDbViolation.isPresent()) {
		  return crossDbViolation;
		}
	  }

	  // Special validation for JavaScript execution
	  if (!allowJavaScriptExecution) {
		var jsViolation = validateJavaScriptExecution(stageOperator, stage.get(stageOperator), location);
		if (jsViolation.isPresent()) {
		  return jsViolation;
		}
	  }

	  // Recursively validate stage content for blacklisted operators
	  var operatorViolation = validateOperators(stage.get(stageOperator), location + "." + stageOperator);
	  if (operatorViolation.isPresent()) {
		return operatorViolation;
	  }
	}

	return Optional.empty();
  }

  /**
   * Validates cross-database access restrictions for specific stages.
   */
  private Optional<SecurityViolation> validateCrossDatabaseAccess(String stageOperator, BsonValue stageValue,
																  String requestDatabase, String location) {
	var stageOperatorLower = stageOperator.toLowerCase();

	// Check stages that can access other collections/databases
	return switch (stageOperatorLower) {
	  case "$lookup", "$graphlookup", "$unionwith" ->
		  validateCollectionReference(stageValue, requestDatabase, location, stageOperator);
	  case "$out", "$merge" -> validateOutputReference(stageValue, requestDatabase, location, stageOperator);
	  default -> Optional.empty();
	};
  }

  /**
   * Validates collection references in lookup-type stages.
   */
  private Optional<SecurityViolation> validateCollectionReference(BsonValue stageValue, String requestDatabase,
																  String location, String stageOperator) {
	if (!stageValue.isDocument()) {
	  return Optional.empty();
	}

	var stageDoc = stageValue.asDocument();
	BsonValue collectionRef = null;

	// Extract collection reference based on stage type
	if (stageDoc.containsKey("from")) {
	  collectionRef = stageDoc.get("from");
	} else if (stageDoc.containsKey("coll")) {
	  collectionRef = stageDoc.get("coll");
	}

	if (collectionRef != null && collectionRef.isString()) {
	  String collectionName = collectionRef.asString().getValue();

	  // Check if collection reference includes database name (db.collection format)
	  if (collectionName.contains(".")) {
		String[] parts = collectionName.split("\\.", 2);
		String referencedDatabase = parts[0];

		if (!referencedDatabase.equals(requestDatabase)) {
		  return Optional.of(new SecurityViolation(
			  "CROSS_DATABASE_ACCESS",
			  stageOperator,
			  location,
			  String.format("Stage references database '%s' but request is for database '%s'",
				  referencedDatabase, requestDatabase)
		  ));
		}
	  }
	}

	return Optional.empty();
  }

  /**
   * Validates output references in $out and $merge stages.
   */
  private Optional<SecurityViolation> validateOutputReference(BsonValue stageValue, String requestDatabase,
															  String location, String stageOperator) {
	String targetCollection = null;
	String targetDatabase = null;

	if (stageValue.isString()) {
	  // Simple collection name
	  targetCollection = stageValue.asString().getValue();
	} else if (stageValue.isDocument()) {
	  var stageDoc = stageValue.asDocument();

	  // Extract target from various formats
	  if (stageDoc.containsKey("into")) {
		var into = stageDoc.get("into");
		if (into.isString()) {
		  targetCollection = into.asString().getValue();
		} else if (into.isDocument()) {
		  var intoDoc = into.asDocument();
		  if (intoDoc.containsKey("db")) {
			targetDatabase = intoDoc.get("db").asString().getValue();
		  }
		  if (intoDoc.containsKey("coll")) {
			targetCollection = intoDoc.get("coll").asString().getValue();
		  }
		}
	  }
	}

	// Check for explicit database reference
	if (targetDatabase != null && !targetDatabase.equals(requestDatabase)) {
	  return Optional.of(new SecurityViolation(
		  "CROSS_DATABASE_OUTPUT",
		  stageOperator,
		  location,
		  String.format("Stage outputs to database '%s' but request is for database '%s'",
			  targetDatabase, requestDatabase)
	  ));
	}

	// Check for db.collection format in collection name
	if (targetCollection != null && targetCollection.contains(".")) {
	  String[] parts = targetCollection.split("\\.", 2);
	  String referencedDatabase = parts[0];

	  if (!referencedDatabase.equals(requestDatabase)) {
		return Optional.of(new SecurityViolation(
			"CROSS_DATABASE_OUTPUT",
			stageOperator,
			location,
			String.format("Stage outputs to database '%s' but request is for database '%s'",
				referencedDatabase, requestDatabase)
		));
	  }
	}

	return Optional.empty();
  }

  /**
   * Validates JavaScript execution restrictions.
   */
  private Optional<SecurityViolation> validateJavaScriptExecution(String stageOperator, BsonValue stageValue,
																  String location) {
	var stageOperatorLower = stageOperator.toLowerCase();

	// Check for stages that commonly execute JavaScript
	if (stageOperatorLower.equals("$where") ||
		stageOperatorLower.equals("$function") ||
		stageOperatorLower.equals("$accumulator")) {

	  return Optional.of(new SecurityViolation(
		  "JAVASCRIPT_EXECUTION",
		  stageOperator,
		  location,
		  "JavaScript execution is disabled by security policy"
	  ));
	}

	return Optional.empty();
  }

  /**
   * Recursively validates BSON value for blacklisted operators.
   */
  private Optional<SecurityViolation> validateOperators(BsonValue value, String location) {
	if (value == null) {
	  return Optional.empty();
	}

	if (value.isDocument()) {
	  return validateOperators(value.asDocument(), location);
	} else if (value.isArray()) {
	  return validateOperators(value.asArray(), location);
	}

	return Optional.empty();
  }

  /**
   * Recursively validates BSON document for blacklisted operators.
   */
  private Optional<SecurityViolation> validateOperators(BsonDocument doc, String location) {
	if (doc == null) {
	  return Optional.empty();
	}

	for (String key : doc.keySet()) {
	  var keyLower = key.toLowerCase();

	  // Check if this key is a blacklisted operator
	  if (operatorBlacklist.contains(keyLower)) {
		return Optional.of(new SecurityViolation(
			"BLACKLISTED_OPERATOR",
			key,
			location,
			"Operator is in security blacklist"
		));
	  }

	  // Recursively check the value
	  var violation = validateOperators(doc.get(key), location + "." + key);
	  if (violation.isPresent()) {
		return violation;
	  }
	}

	return Optional.empty();
  }

  /**
   * Recursively validates BSON array for blacklisted operators.
   */
  private Optional<SecurityViolation> validateOperators(BsonArray array, String location) {
	if (array == null) {
	  return Optional.empty();
	}

	for (int i = 0; i < array.size(); i++) {
	  var violation = validateOperators(array.get(i), location + "[" + i + "]");
	  if (violation.isPresent()) {
		return violation;
	  }
	}

	return Optional.empty();
  }

  /**
   * Convenience method to validate a pipeline and throw SecurityException if invalid.
   */
  public void validatePipelineOrThrow(BsonArray pipeline, String requestDatabase) {
	var violation = validatePipeline(pipeline, requestDatabase);
	if (violation.isPresent()) {
	  var v = violation.get();
	  LOGGER.warn("Aggregation pipeline security violation: {}", v);
	  throw new SecurityException(v.toString());
	}
  }

  /**
   * Returns true if security checking is enabled.
   */
  public boolean isEnabled() {
	return enabled;
  }

  /**
   * Returns the configured stage blacklist.
   */
  public Set<String> getStageBlacklist() {
	return Set.copyOf(stageBlacklist);
  }

  /**
   * Returns the configured operator blacklist.
   */
  public Set<String> getOperatorBlacklist() {
	return Set.copyOf(operatorBlacklist);
  }
}