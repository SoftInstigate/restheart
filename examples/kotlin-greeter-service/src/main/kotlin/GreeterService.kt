import org.restheart.exchange.JsonRequest
import org.restheart.exchange.JsonResponse
import org.restheart.exchange.ExchangeKeys.METHOD
import org.restheart.plugins.JsonService
import org.restheart.plugins.RegisterPlugin
import org.restheart.utils.HttpStatus
import org.restheart.utils.GsonUtils.`object` as obj

@RegisterPlugin(name = "kotlinGreeterService", description = "just another Hello World in Kotlin")
class GreeterService : JsonService {
    override fun handle(req: JsonRequest, res: JsonResponse) {
        when(req.method) {
            METHOD.GET -> res.content = obj().put("msg", "Hello World").get()
            METHOD.OPTIONS -> handleOptions(req);
            else -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}