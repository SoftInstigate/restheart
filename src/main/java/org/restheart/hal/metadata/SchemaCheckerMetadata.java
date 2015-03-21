/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.hal.metadata;

import com.mongodb.DBObject;
import javax.script.ScriptException;
import org.bson.types.Code;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SchemaCheckerMetadata extends ScriptMetadata {
    public static final String SCHEMA_ELEMENT_NAME = "schemaCheck";
    public static final String SCHEMA_CODE_ELEMENT_NAME = "code";
    public static final String SCHEMA_ARGS_ELEMENT_NAME = "args";

    private final Code code;
    private final DBObject args;

    private static final String JSD_VALIDATOR = "function JSDValidator(e){this.Error=null;this.SetSchema=function(e){v=e};this.Validate=function(e){if(typeof JSON!==\"object\")return h(\"JSON is undefined. Did you forget to include json2.js?\");if(!m)return h(d.Error);d.Error=null;return t(e,v)};this.ValidateSchema=function(e){m=false;if(e==undefined)e=v;if(e==undefined)return h(\"No JSD Schema defined\");d.Error=null;m=c(e);return m};var t=function(e,t,c){if(t==undefined)t=v;if(t==undefined)return h(\"No JSD Schema defined\");var d=e!==undefined;if(t.Condition instanceof Function){if(d){if(!t.Condition(e,c))return p(\"errConditionNotMet\",t,e)}else if(t.Condition(e,c)&&!t.Optional)return p(\"errUnfoundNonOptional\",t,e)}else{if(!t.Optional&&!d)return p(\"errUnfoundNonOptional\",t,e)}if(d){if(e==null){if(!t.CanBeNull)return p(\"errCantBeNull\",t,e);return true}var m=false;if(t.Type){switch(t.Type){case\"String\":m=n(e,t);break;case\"Number\":m=r(e,t);break;case\"Boolean\":m=i(e,t);break;case\"Date\":m=s(e,t);break;case\"Array\":m=o(e,t);break;case\"Object\":m=u(e,t);break;case\"RegExp\":m=a(e,t);break;case\"Function\":m=f(e,t);break;case\"MultiType\":m=l(e,t);break}if(!m)return false}if(t.Validate&&!t.Validate(e,c))return p(\"errValidate\",t,e);}return true};var n=function(e,t){if(typeof e!=\"string\")return p(\"errWrongType\",t,e);if(t.MinLength!=undefined&&e.length<t.MinLength)return p(\"errMinLength\",t,e);if(t.MaxLength!=undefined&&e.length>t.MaxLength)return p(\"errMaxLength\",t,e);if(t.RegExp&&!t.RegExp.test(e))return p(\"errRegExp\",t,e);if(t.Values){var n=false;for(var r=0;r<t.Values.length;r++){if(e==t.Values[r]){n=true;break}}if(!n)return p(\"errNoMatchedValue\",t,e)}if(t.NotValues){for(var r=0;r<t.NotValues.length;r++){if(e==t.NotValues[r])return p(\"errMatchedNotValue\",t,e)}}return true};var r=function(e,t){if(typeof e!=\"number\")return p(\"errWrongType\",t,e);if(t.Max!=undefined&&e>t.Max)return p(\"errMax\",t,e);if(t.Min!=undefined&&e<t.Min)return p(\"errMin\",t,e);if(t.Unsigned&&e<0)return p(\"errSigned\",t,e);if(t.Integer&&e%Math.floor(e)>0)return p(\"errInteger\",t,e);if(t.Values){var n=false;for(var r=0;r<t.Values.length;r++){if(e==t.Values[r]){n=true;break}}if(!n)return p(\"errNoMatchedValue\",t,string)}if(t.NotValues){for(var r=0;r<t.NotValues.length;r++){if(e==t.NotValues[r])return p(\"errMatchedNotValue\",t,string)}}return true};var i=function(e,t){if(typeof e!=\"boolean\")return p(\"errWrongType\",t,e);if(t.Values){var n=false;for(var r=0;r<t.Values.length;r++){if(e==t.Values[r]){n=true;break}}if(!n)return p(\"errNoMatchedValue\",t,string)}if(t.NotValues){for(var r=0;r<t.NotValues.length;r++){if(e==t.NotValues[r])return p(\"errMatchedNotValue\",t,string)}}return true};var s=function(e,t){if(!(e instanceof Date))return p(\"errWrongType\",t,e);if(t.MinDate!=undefined&&e<t.MinDate)return p(\"errMinDate\",t,e);if(t.MaxDate!=undefined&&e>t.MaxDate)return p(\"errMaxDate\",t,e);if(t.Values){var n=false;for(var r=0;r<t.Values.length;r++){if(e==t.Values[r]){n=true;break}}if(!n)return p(\"errNoMatchedValue\",t,e)}if(t.NotValues){for(var r=0;r<t.NotValues.length;r++){if(e==t.NotValues[r])return p(\"errMatchedNotValue\",t,e)}}return true};var o=function(e,n){if(!(e instanceof Array))return p(\"errWrongType\",n,e);if(n.MinSize!=undefined&&e.length<n.MinSize)return p(\"errMinSize\",n,e);if(n.MaxSize!=undefined&&e.length>n.MaxSize)return p(\"errMaxSize\",n,e);if(n.Values){for(var r=0;r<e.length;r++){var i=e[r];var s=false;for(var o=0;o<n.Values.length;o++){var u=n.Values[o];if(t(i,u)){s=true;break}else if(n.Values.length===1){return false}}if(!s)return p(\"errNoMatchedValue\",n,e)}}if(n.NotValues){for(var r=0;r<e.length;r++){var i=e[r];for(var o=0;o<n.NotValues.length;o++){var u=n.NotValues[o];if(t(i,u)){d.Error=null;return p(\"errMatchedNotValue\",n,e)}}}d.Error=null}return true};var u=function(e,n){if(!(e instanceof Object))return p(\"errWrongType\",n,e);if(n.Attributes){for(var r=0;r<n.Attributes.length;r++){var i=n.Attributes[r];if(!t(e[i.Name],i,e))return false}}if(!n.CanHaveCustomAttributes){for(var s in e){if(!e.hasOwnProperty(s))continue;var o=false;if(n.Attributes){for(var r=0;r<n.Attributes.length;r++){var i=n.Attributes[r];if(s==i.Name){o=true;break}}}if(!o)return p(\"errCustomAttribute\",n,e,{key:s})}}return true};var a=function(e,t){if(!(e instanceof RegExp))return p(\"errWrongType\",t,e);if(t.Values){var n=false;for(var r=0;r<t.Values.length;r++){if(e==t.Values[r]){n=true;break}}if(!n)return p(\"errNoMatchedValue\",t,e)}if(t.NotValues){for(var r=0;r<t.NotValues.length;r++){if(e==t.NotValues[r])return p(\"errMatchedNotValue\",t,e)}}return true};var f=function(e,t){if(!(e instanceof Function))return p(\"errWrongType\",t,e);return true};var l=function(e,n){var r=false;for(var i=0;i<n.Types.length;i++){if(t(e,n.Types[i])){r=true;break}}d.Error=false;if(!r)return p(\"errMultiType\",n,e);return true};var c=function(e){var t={Type:\"Object\",Attributes:[{Name:\"Type\",Type:\"String\",Values:[\"String\",\"Number\",\"Boolean\",\"Date\",\"Array\",\"Object\",\"Boolean\",\"Function\",\"MultiType\"]},{Name:\"CanBeNull\",Type:\"Boolean\",Optional:true},{Name:\"Values\",Type:\"Array\",Optional:true},{Name:\"NotValues\",Type:\"Array\",Optional:true},{Name:\"Validate\",Type:\"Function\",Optional:true},{Name:\"Condition\",Type:\"Function\",Optional:true},{Name:\"MaxSize\",Type:\"Number\",Optional:true,Integer:true,Condition:function(e,t){return t.Type==\"Array\"}},{Name:\"MinSize\",Type:\"Number\",Optional:true,Integer:true,Condition:function(e,t){return t.Type==\"Array\"}},{Name:\"MaxLength\",Type:\"Number\",Optional:true,Integer:true,Condition:function(e,t){return t.Type==\"String\"}},{Name:\"MinLength\",Type:\"Number\",Optional:true,Integer:true,Condition:function(e,t){return t.Type==\"String\"}},{Name:\"RegExp\",Type:\"RegExp\",Optional:true,Condition:function(e,t){return t.Type==\"String\"}},{Name:\"Min\",Type:\"Number\",Optional:true,Condition:function(e,t){return t.Type==\"Number\"}},{Name:\"Max\",Type:\"Number\",Optional:true,Condition:function(e,t){return t.Type==\"Number\"}},{Name:\"Unsigned\",Type:\"Boolean\",Optional:true,Condition:function(e,t){return t.Type==\"Number\"}},{Name:\"Integer\",Type:\"Boolean\",Optional:true,Condition:function(e,t){return t.Type==\"Number\"}},{Name:\"MinDate\",Type:\"Date\",Optional:true,Condition:function(e,t){return t.Type==\"Date\"}},{Name:\"MaxDate\",Type:\"Date\",Optional:true,Condition:function(e,t){return t.Type==\"Date\"}},{Name:\"Types\",Type:\"Array\",Optional:true,Values:\"Object\",Condition:function(e,t){return t.Type==\"MultiType\"}},{Name:\"Attributes\",Type:\"Array\",Optional:true,Condition:function(e,t){return t.Type==\"Object\"}},{Name:\"CanHaveCustomAttributes\",Type:\"Boolean\",Optional:true,Condition:function(e,t){return t.Type==\"Object\"}}],CanHaveCustomAttributes:false};if(typeof JSON!==\"object\")return h(\"JSON is undefined. Did you forget to include json2.js?\");jsd=new JSDValidator({Schema:t});var n=jsd.Validate(e);if(n)return true;return h(jsd.Error)};var h=function(e){if(!d.Error)d.Error=e;return false};var p=function(e,t,n,r){if(t[e]!==undefined&&typeof t[e]==\"string\")return h(t[e]);var i;try{i=JSON.stringify(n,undefined,2)}catch(s){if(s.type==\"circular_structure\")i=\"circulared object\"}switch(e){case\"errWrongType\":return h(typeof n+\" is not a \"+t.Type+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errNoMatchedValue\":return h(typeof n+\" does not match any of the given values [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errMatchedNotValue\":return h(typeof n+\" matches one of the NotValues [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errConditionNotMet\":return h(\"Condition did not get met on \"+typeof n+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errUnfoundNonOptional\":return h(typeof n+\" not found while not optional [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errCantBeNull\":return h(\"Object can't be null [\"+(t.Name?t.Name+\": \":\"\")+\"\"+i+\"]\");case\"errValidate\":return h(typeof n+\" did not pass the Validate function [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errMinLength\":return h(\"String is smaller than MinLength \"+t.MinLength+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errMaxLength\":return h(\"String is greater than MaxLength \"+t.MaxLength+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errRegExp\":return h(\"String does not validate with RegExp \"+t.RegExp.toString()+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errMax\":return h(\"Number is bigger than Max \"+t.Max+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errMin\":return h(\"Number is smaller than Min \"+t.Min+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errSigned\":return h(\"Number is Signed [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errInteger\":return h(\"Number is not an Integer [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errMinDate\":return h(\"Date is sooner than MinDate \"+t.MinDate+\" [\"+(t.Name?t.Name+\": \":\"\")+\"\"+n.toString()+\"]\");case\"errMaxDate\":return h(\"Date is later than MaxDate \"+t.MaxDate+\" [\"+(t.Name?t.Name+\": \":\"\")+\"\"+n.toString()+\"]\");case\"errMinSize\":return h(\"Array is smaller than MinSize \"+t.MinSize+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errMaxSize\":return h(\"Array is bigger than MaxSize \"+t.MaxSize+\" [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errCustomAttribute\":return h(\"Attribute '\"+r.key+\"' is a custom attribute, while the schema does not have 'CanHaveCustomAttributes' set to true [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\");case\"errMultiType\":return h(typeof n+\" did not validate on any type of the multi types [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+i+\"']\")}return h(\"Object did not validate [\"+(t.Name?t.Name+\": \":\"\")+\"'\"+n.toString()+\"']\")};var d=this;var v=undefined;var m=true;if(e!=undefined){if(e.Schema)v=e.Schema;if(e.ValidateSchema){m=c(v)}}return true}function JSD(){}JSD.Link=function(e,t){function n(e){if(e==null||typeof e!=\"object\")return e;var t=e.constructor();for(var r in e)t[r]=n(e[r]);return t}var r=n(e);for(var i in t){if(!t.hasOwnProperty(i))continue;r[i]=t[i]}return r};";

    /**
     *
     * @param code
     * @param args
     * @throws javax.script.ScriptException
     */
    public SchemaCheckerMetadata(Code code, DBObject args) throws ScriptException {
        super(getCodePlusObjAndScrips(code, args));
        this.code = code;
        this.args = args;
    }

    private static String getCodePlusObjAndScrips(Code code, DBObject obj) {
        String script = code.getCode();

        if (script.contains("JSDValidator")) {
            script = JSD_VALIDATOR + "\n"
                    + script;
        }

        if (obj != null) {
            script = "var obj = JSON.parse('" + obj.toString() + "');" + "\n"
                    + script;
        }

        return script;
    }

    /**
     * @return the code
     */
    public Code getCode() {
        return code;
    }

    /**
     * @return the args
     */
    public DBObject getArgs() {
        return args;
    }

    public static SchemaCheckerMetadata getFromJson(DBObject props, boolean checkCode) throws InvalidMetadataException {
        Code code;
        DBObject obj = null;

        if (checkCode) {
            code = checkCodeFromJson((DBObject) props);
        } else {
            Object _sc = props.get(SCHEMA_ELEMENT_NAME);

            if (_sc == null || !(_sc instanceof DBObject)) {
                throw new InvalidMetadataException((_sc == null ? "missing '" : "invalid '") + SCHEMA_ELEMENT_NAME + "' element. it must be a json object");
            }

            DBObject sc = (DBObject) _sc;

            Object _code = sc.get(SCHEMA_CODE_ELEMENT_NAME);

            if (_code == null || !(_code instanceof Code)) {
                throw new InvalidMetadataException((_code == null ? "missing '" : "invalid '") + SCHEMA_CODE_ELEMENT_NAME + "' element. it must be of type $code");
            }

            code = (Code) _code;

            Object _obj = sc.get(SCHEMA_ARGS_ELEMENT_NAME);

            if (_obj != null && !(_obj instanceof DBObject)) {
                throw new InvalidMetadataException("invalid '" + SCHEMA_ARGS_ELEMENT_NAME + "' element. it must be a json object");
            }

            obj = (DBObject) _obj;
        }

        try {
            return new SchemaCheckerMetadata(code, obj);
        } catch (ScriptException ex) {
            throw new InvalidMetadataException("cannot compile scrip", ex);
        }
    }

    private static Code checkCodeFromJson(DBObject props) throws InvalidMetadataException {
        Object _sc = props.get(SCHEMA_ELEMENT_NAME);

        if (_sc == null || !(_sc instanceof DBObject)) {
            throw new InvalidMetadataException((_sc == null ? "missing '" : "invalid '") + SCHEMA_ELEMENT_NAME + "' element. it must be a json object");
        }

        DBObject sc = (DBObject) _sc;

        Object _code = sc.get(SCHEMA_CODE_ELEMENT_NAME);

        if (_code == null || !(_code instanceof Code)) {
            throw new InvalidMetadataException((_code == null ? "missing '" : "invalid '") + SCHEMA_CODE_ELEMENT_NAME + "' element. it must be of type $code");
        }

        Object _obj = sc.get(SCHEMA_ARGS_ELEMENT_NAME);

        if (_obj != null && !(_obj instanceof DBObject)) {
            throw new InvalidMetadataException("invalid '" + SCHEMA_ARGS_ELEMENT_NAME + "' element. it must be a json object");
        }

        String script = getCodePlusObjAndScrips((Code) _code, (DBObject) _obj);

        // check code
        try {
            evaluate(script, getTestBindings());
        } catch (ScriptException se) {
            throw new InvalidMetadataException("invalid javascript code", se);
        }

        return (Code) _code;
    }
}
