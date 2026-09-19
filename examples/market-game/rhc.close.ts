/**
 * Close the game, and leave the record standing.
 *
 *   rhc setup --srv <srvId> --file rhc.close.ts            # revoke every write
 *   rhc setup --srv <srvId> --file rhc.close.ts --dry-run  # say what is still open
 *
 * The account everybody plays through has a password printed in this repository, on purpose: it
 * is how three agents behind one connector prove who is speaking. That is fine while a match is
 * running and wrong the moment it is not, because a service left configured is a service anybody
 * can append to. This takes the writing away.
 *
 * What it removes is the three `appendsAs_<player>` permissions, which are the only rules that
 * allow a POST anywhere. Reading stays: the ledger, the aggregations, the objectives and the MCP
 * endpoint all answer as before, so `watch.html` still draws the match and the connector still
 * lists it. The ledger was append-only and is now not even that — history is closed, and a
 * password in the open buys nothing but a look.
 *
 * To play again, run the ordinary setup: `rhc setup --srv <srvId>` puts the rules back, and
 * `--force game` deals a fresh board. To close the service completely instead, delete the `table`
 * user in the console; nothing else on the service answers to that password.
 */
import { defineSetup, step } from '@restheart-cloud/cli';

import { remove, stored } from './game/service.ts';
import { TRADERS } from './game/reference.ts';

/** Every rule that permits a write. The reading ones are deliberately left alone. */
const WRITING_RULES = TRADERS.map(player => `appendsAs_${player}`);

export default defineSetup('Market game, closed', [
  ...WRITING_RULES.map(id =>
    step(`${id} is revoked`, {
      check: async ({ service }) => (await stored(service, `/acl/${id}`)) === null,
      apply: ({ service }) => remove(service, `/acl/${id}`),
    })
  ),
]);
