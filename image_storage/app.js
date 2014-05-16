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
    for (var k in req.body) {
        console.log(k);
    }

    var name= fs.readdirSync(dirToStore).length + 1;
    var url = 'http://' + req.headers.host + '/uploads/' + name + '.jpg';;

    console.log(req.files);

    fs.readFile(req.files.image.path, function (err, data) {
        fs.writeFile(dirToStore + '/' + name + '.jpg', data, function(err) {
            console.log('saved ' + url);
        });
        //res.writeHead(200, 'application/json');
        res.set('Content-Type', 'application/json');
        var urlForGet = 'http://' + req.headers.host + '/img/' + name;
        res.end(JSON.stringify({"url":urlForGet}));
    });
});

app.get('/img/:id', function(req, res) {
    var path = dirToStore + '/' + req.params.id + '.jpg';
    console.log('id = ' + req.params.id + ', path=' + path);
    res.sendfile(path);
});

app.listen(2014);
