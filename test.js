onError= (e) => { console.log("ERROR", e); }

window.requestFileSystem(PERSISTENT, 1024 * 1024, function(fs) {
		 fs.root.getFile('hexadecimal.zip', {create: false}, function(fileEntry) {
				 fileUrl = fileEntry.toURL();
			 }, onError);

		 fs.root.getDirectory('tmp', {create: true}, function(fileEntry) {
			 dirUrl = fileEntry.toURL();
			 }, onError);
		 }, onError);

zip.unzip_str(fileUrl, "hexadecimal/hexadecimal.js", (d, e) => console.log("DATO", ab= d, "e", e), (d) => console.log("P", d));

s= String.fromCharCode.apply(null, new Uint8Array(ab));
