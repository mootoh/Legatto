var fs = require('fs');
var express = require("express");
var multipart = require('connect-multiparty');
var multipartMiddleware = multipart();

var app = express();
var dirToStore = __dirname + '/uploads';

app.get('/', function(req, res) {
    var form = "<!DOCTYPE HTML><html><body>" +
    "<form method='post' action='/upload' enctype='multipart/form-data'>" +
    "<input type='file' name='image'/>" +
    "<input type='submit' /></form>" +
    "</body></html>";

    res.writeHead(200, {'Content-Type': 'text/html'});
    res.end(form);
});

app.post('/upload', multipartMiddleware, function(req, res) {
    fs.readFile(req.files.image.path, function (err, data) {
        var name= (fs.readdirSync(dirToStore).length + 1) + '.jpg';

        var url = 'http://' + req.headers.host + '/uploads/' + name;

        fs.writeFile(dirToStore + '/' + name, data, function(err) {
            console.log('saved ' + url);
        });
        res.writeHead(200, {'Content-Type': 'text/html'});
        res.end(url);
    });
});

app.get('/img/:id', function(req, res) {
    var path = dirToStore + '/' + req.params.id + '.jpg';
    console.log('id = ' + req.params.id + ', path=' + path);
    res.sendfile(path);
});

app.listen(2014);
