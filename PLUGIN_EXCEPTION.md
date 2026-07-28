# RESTHEART PLUGIN EXCEPTION

Version 1.2, effective from RESTHeart 9.0

Copyright (C) 2026 SoftInstigate S.R.L.

This Plugin Exception clarifies the permissions already available to Plugin developers as a consequence of RESTHeart's licensing structure, and, to the extent any doubt might otherwise arise, additionally serves as an additional permission granted under Section 7 of the GNU Affero General Public License version 3 ("GNU AGPL v3"), as published by the Free Software Foundation. It does not itself alter or restrict any rights already granted under the separate license of the Approved Interfaces described below.

1. __DEFINITIONS__

"RESTHeart" means the software distributed by SoftInstigate S.R.L. under the GNU AGPL v3.

"Approved Interfaces" means the public APIs provided by the `restheart-commons` artifact (Maven group ID: `org.restheart`, artifact ID: `restheart-commons`), as published on Maven Central and explicitly designated by SoftInstigate S.R.L. as Approved Interfaces in the official RESTHeart documentation for the applicable version. Unlike RESTHeart's core, `restheart-commons` is distributed under the **Apache License 2.0**, a separate and independent license from the GNU AGPL v3.

"Plugin" means an independent software module that communicates with RESTHeart solely through the Approved Interfaces, including but not limited to communication via static or dynamic linking, embedding, or runtime coupling, and that does not incorporate, modify, or derive from any RESTHeart source code other than the Approved Interfaces.

2. __CLARIFICATION AND ADDITIONAL PERMISSION__

Because the Approved Interfaces are licensed separately under the Apache License 2.0, a Plugin that depends solely on the Approved Interfaces is not, merely by virtue of that dependency, subject to the copyleft obligations of the GNU AGPL v3. This Section confirms that understanding and, to the extent Section 7 of the GNU AGPL v3 might otherwise be read to require more, grants the same permission as an additional permission under that Section: you may develop, distribute, and sublicense such Plugins under terms of your choice, including proprietary terms, notwithstanding any copyleft obligations under the GNU AGPL v3.

3. __CONDITIONS__

This permission applies only if the Plugin:

(a) depends solely on the Approved Interfaces, and does not use any internal, non-public, or AGPL-licensed APIs of RESTHeart; and

(b) does not modify RESTHeart core components.

4. __SCOPE__

This permission applies only to versions of RESTHeart distributed under the GNU AGPL v3. It does not apply to versions of RESTHeart distributed under any other license.

5. __NO EFFECT ON RESTHEART ITSELF__

This additional permission does not alter the licensing of RESTHeart itself. All modifications to RESTHeart core remain subject to the GNU AGPL v3 in full.

6. __NO WARRANTY__

This additional permission is granted without warranty of any kind. SoftInstigate S.R.L. makes no representation that this permission is sufficient to satisfy any particular legal requirement in any jurisdiction. Recipients are responsible for obtaining their own legal advice.