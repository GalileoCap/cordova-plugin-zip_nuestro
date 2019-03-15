var exec = cordova.require('cordova/exec');

function newProgressEvent(result) {
    var event = {
            loaded: result.loaded,
            total: result.total
    };
    return event;
}

exports.unzip = function(fileName, outputDirectory, callback, progressCallback) {
    var win = function(result) {
        if (result && typeof result.loaded != "undefined") {
            if (progressCallback) { return progressCallback(newProgressEvent(result)); }
        } else if (callback) { callback(0); }
    };
    var fail = function(result) { if (callback) { callback(-1); } };
    exec(win, fail, 'Zip', 'unzip', [fileName, outputDirectory]);
};

exports.unzip_dir = function(fileName, callback) {
    var win = function(result) { if (callback) { callback(result); } };
    var fail = function(result) { if (callback) { callback(null, result); } };
    exec(win, fail, 'Zip', 'unzip_dir', [fileName]);
};

exports.unzip_str = function(fileName, compressedPath, callback) {
    var win = function(result) { if (callback) { callback(result); } };
    var fail = function(result) { if (callback) { callback(null, result); } };
    exec(win, fail, 'Zip', 'unzip_str', [fileName, compressedPath]);
};

exports.unzip_str_zip4j = function(fileName, pass, compressedPath, callback) {
    var win = function(result) { if (callback) { callback(result); } };
    var fail = function(result) { if (callback) { callback(null, result); } };
    exec(win, fail, 'Zip', 'unzip_str_zip4j', [fileName, pass, compressedPath]);
};


