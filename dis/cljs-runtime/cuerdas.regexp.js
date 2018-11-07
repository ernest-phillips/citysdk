goog.provide('cuerdas.regexp');
goog.require('cljs.core');
goog.require('cuerdas.vendor.xregexp');
goog.require('goog.string');
cuerdas.regexp.xregexp = XRegExp;
/**
 * Return `true` if `x` is a regexp pattern
 *   instance.
 */
cuerdas.regexp.regexp_QMARK_ = (function cuerdas$regexp$regexp_QMARK_(x){
return cljs.core.regexp_QMARK_(x);
});
/**
 * Enhace the regexp instance with unicode support. This is noop
 *   in JVM because it already works perfectly with unicode.
 */
cuerdas.regexp.enhace = (function cuerdas$regexp$enhace(re){
if(cljs.core.truth_(cuerdas.regexp.regexp_QMARK_(re))){
} else {
throw (new Error("Assert failed: (regexp? re)"));
}

var G__24404 = re.source;
var G__24405 = re.flags;
return (cuerdas.regexp.xregexp.cljs$core$IFn$_invoke$arity$2 ? cuerdas.regexp.xregexp.cljs$core$IFn$_invoke$arity$2(G__24404,G__24405) : cuerdas.regexp.xregexp.call(null,G__24404,G__24405));
});
/**
 * Escapes characters in the string that are not safe
 * to use in a RegExp.
 */
cuerdas.regexp.escape = (function cuerdas$regexp$escape(s){
return goog.string.regExpEscape(s);
});

//# sourceMappingURL=cuerdas.regexp.js.map
