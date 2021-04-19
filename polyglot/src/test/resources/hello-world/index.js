export function handle(request, response) {
    LOGGER.debug('request {}', request.getContent());
    const rc = JSON.parse(request.getContent() || '{}');

    let body = {
        msg: `Hello ${rc.name || 'Cruel World'}`
    }

    response.setContent(JSON.stringify(body));
    response.setContentTypeAsJson();
}