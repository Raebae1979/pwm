/*
	Copyright (c) 2004-2011, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.highlight.languages.pygments.javascript"]){
dojo._hasResource["dojox.highlight.languages.pygments.javascript"]=true;
dojo.provide("dojox.highlight.languages.pygments.javascript");
dojo.require("dojox.highlight._base");
(function(){
var dh=dojox.highlight,_1=dh.constants;
dh.languages.javascript={defaultMode:{lexems:["\\b[a-zA-Z]+"],keywords:{"keyword":{"for":1,"in":1,"while":1,"do":1,"break":1,"return":1,"continue":1,"if":1,"else":1,"throw":1,"try":1,"catch":1,"var":1,"with":1,"const":1,"label":1,"function":1,"new":1,"typeof":1,"instanceof":1},"keyword constant":{"true":1,"false":1,"null":1,"NaN":1,"Infinity":1,"undefined":1},"name builtin":{"Array":1,"Boolean":1,"Date":1,"Error":1,"Function":1,"Math":1,"netscape":1,"Number":1,"Object":1,"Packages":1,"RegExp":1,"String":1,"sun":1,"decodeURI":1,"decodeURIComponent":1,"encodeURI":1,"encodeURIComponent":1,"Error":1,"eval":1,"isFinite":1,"isNaN":1,"parseFloat":1,"parseInt":1,"document":1,"window":1},"name builtin pseudo":{"this":1}},contains:["comment single","comment multiline","number integer","number oct","number hex","number float","string single","string double","string regex","operator","punctuation","_function"]},modes:[{className:"comment single",begin:"//",end:"$",relevance:0},{className:"comment multiline",begin:"/\\*",end:"\\*/"},{className:"number integer",begin:"0|([1-9][0-9]*)",end:"^",relevance:0},{className:"number oct",begin:"0[0-9]+",end:"^",relevance:0},{className:"number hex",begin:"0x[0-9a-fA-F]+",end:"^",relevance:0},{className:"number float",begin:"([1-9][0-9]*\\.[0-9]*([eE][\\+-]?[0-9]+)?)|(\\.[0-9]+([eE][\\+-]?[0-9]+)?)|([0-9]+[eE][\\+-]?[0-9]+)",end:"^",relevance:0},{className:"string single",begin:"'",end:"'",illegal:"\\n",contains:["string escape"],relevance:0},{className:"string double",begin:"\"",end:"\"",illegal:"\\n",contains:["string escape"],relevance:0},{className:"string escape",begin:"\\\\.",end:"^",relevance:0},{className:"string regex",begin:"/.*?[^\\\\/]/[gim]*",end:"^"},{className:"operator",begin:"\\|\\||&&|\\+\\+|--|-=|\\+=|/=|\\*=|==|[-\\+\\*/=\\?:~\\^]",end:"^",relevance:0},{className:"punctuation",begin:"[{}\\(\\)\\[\\]\\.;]",end:"^",relevance:0},{className:"_function",begin:"function\\b",end:"{",lexems:[_1.UNDERSCORE_IDENT_RE],keywords:{keyword:{"function":1}},contains:["name function","_params"],relevance:5},{className:"name function",begin:_1.UNDERSCORE_IDENT_RE,end:"^"},{className:"_params",begin:"\\(",end:"\\)",contains:["comment single","comment multiline"]}]};
})();
}
