var document = { URL: "whatever",
  writeln: function Document_prototype_writeln(x) { },
  write: function Document_prototype_write(x) { } };
var id = function _id(x) {
  var id2 = function _id2(x) { return x; };  
  return id2(x);
}; 
function Id() { this.id = id; }
function SubId() { }; SubId.prototype = new Id();
function isSafe(x) {
    return x.startsWith("https:");
}

if (Math.random.call(null) > 0) {
    var id1 = new Id();
    var text = id1.id.call(document, document.URL);
    document.write(text);
    if (isSafe(text)) {
	document.write(text);
    }
} else {
    var id2 = new SubId();
    var text = id2.id("not a url");
    document.writeln(text);
}
