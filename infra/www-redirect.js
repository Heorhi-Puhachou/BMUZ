// CloudFront Function (cloudfront-js-2.0): www.bmuz.eu/шлях → https://bmuz.eu/шлях, каб сайт меў адзін адрас.
function handler(event) {
    var request = event.request;
    if (request.headers.host.value !== 'www.bmuz.eu') {
        return request;
    }
    var query = [];
    for (var name in request.querystring) {
        var parameter = request.querystring[name];
        var values = parameter.multiValue ? parameter.multiValue : [parameter];
        for (var i = 0; i < values.length; i++) {
            query.push(name + '=' + values[i].value);
        }
    }
    return {
        statusCode: 301,
        statusDescription: 'Moved Permanently',
        headers: {
            location: { value: 'https://bmuz.eu' + request.uri + (query.length ? '?' + query.join('&') : '') }
        }
    };
}
