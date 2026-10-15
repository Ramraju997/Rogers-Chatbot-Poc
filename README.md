# Rogers Assistant (POC)

An Angular chat app backed by Spring Boot and a Google **Dialogflow CX / Conversational Agents** agent that recommends a phone + plan from the customer's needs.

```
Angular (localhost:4200)
   │  /api/chat
   ▼
Spring Boot (localhost:3000) ──detectIntent──▶ Dialogflow CX agent (playbook)
   │                         ◀──tool call─────┘  "recommend_device_plan"
   │  runs RecommendationService on the catalog
   └──────────────────────── tool result ─────▶ agent writes the reply
```

The agent decides *what to ask* and *how to explain*; the Spring Boot `RecommendationService` decides *what to recommend and at what price*. The agent never makes up a price.

## How a recommendation is calculated

1. **Match the device.** "iphone 18" matches both iPhone 18 Pro and Pro Max, so the agent asks which one.
2. **Pair it with each plan that comes with a phone.** Every plan has a `planTierCode`; every device price row has a `tierCode`. Joining them gives the phone's monthly price on that plan. BYOD plans (`NOTERM`) are skipped.
3. **Filter** by data need (light / 60 GB / 100 GB / unlimited), international calling, payment option and budget.
4. **Rank** by total monthly cost (plan + phone) and return the top 3, each with the other ways to pay.

Example, iPhone 18 Pro, 100 GB+ data, Save & Return:

| Plan | Data | Plan | Phone | Total |
| --- | --- | --- | --- | --- |
| 5G+ Essentials | 100 GB | $90 | $43.72 | **$133.72** |
| 5G+ Popular | Unlimited | $105 | $33.72 | **$138.72** |
| Ultimate | Unlimited + 64 countries | $115 | $33.72 | **$148.72** |

## Setup

### 1. Catalog data (not in git)

The catalog files are raw catalog API responses, so they are kept out of this public repo. Put them in `backend/data/`:

```
backend/data/devices.json                    device list (facets + devices)
backend/data/plans.json                      plan list
backend/data/device-prices/<externalId>.json full price list for one device, e.g. IP18P256BUR.json
```

A device without a `device-prices` file still works, but only with plans in its headline price tier.

### 2. Google credentials

Put the service-account key at `backend/service-account.json` (git-ignored), or set `GOOGLE_APPLICATION_CREDENTIALS` to its path. The account needs the **Dialogflow API Client** role.

Project, location and agent ID are in `backend/src/main/resources/application.yml` (override with `PROJECT_ID`, `LOCATION`, `AGENT_ID`).

### 3. Dialogflow agent

Follow [`dialogflow/playbook.md`](dialogflow/playbook.md): paste the new playbook instructions and create the `recommend_device_plan` function tool.

### 4. Run

```bash
# Terminal 1: backend (Java 17+, Maven)
cd backend
mvn spring-boot:run

# Terminal 2: Angular
npm install
npm start
```

Open http://localhost:4200.

Test the recommendation engine on its own:

```bash
curl -X POST http://localhost:3000/api/recommend -H "Content-Type: application/json" \
  -d "{\"deviceQuery\":\"iPhone 18 Pro\",\"dataNeed\":\"heavy\"}"
```

## Project layout

| Path | What it is |
| --- | --- |
| `src/app/` | Angular chat UI with recommendation cards |
| `backend/.../chat/ChatService.java` | Calls Dialogflow and runs the function tool when the agent asks |
| `backend/.../recommend/RecommendationService.java` | Device matching, plan pairing, pricing, ranking |
| `backend/.../catalog/CatalogService.java` | Loads the catalog JSON (swap for live catalog APIs later) |
| `dialogflow/` | Playbook instructions and tool schemas for the console |

## Troubleshooting

- **`PKIX path building failed`** when the backend calls Google: your network inspects HTTPS. Import the company root certificate into the JDK truststore, or ask IT for the approved approach.
- **`PERMISSION_DENIED`**: wrong key file or missing Dialogflow API Client role.
- **The bot answers but no cards appear**: the playbook isn't using the tool yet; check step 3.
- **Catalog files not found**: the backend must run from the `backend` folder, or set `CATALOG_DATA_DIR`.

## Not done yet

- The "Choose this option" link uses a placeholder URL. Replace `catalog.cart-url-template` with the real cart deep-link format.
- Existing-customer upgrades (HUP pricing) are not handled; this uses new-activation (`_NAC`) prices.
