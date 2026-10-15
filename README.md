# Rogers Assistant (POC)

A simple Angular chat app connected to a Google **Dialogflow CX / Conversational Agents** agent.

```
Angular app (localhost:4200)  →  /api/chat  →  Node server (localhost:3000)  →  Dialogflow CX agent
```

The Node server holds the Google credentials so they never reach the browser.

## Prerequisites

- Node.js 20 or newer
- [Google Cloud CLI](https://cloud.google.com/sdk/docs/install) (`gcloud`)
- A Conversational Agents agent with the Dialogflow API enabled and billing linked

## 1. Sign in to Google Cloud (one time)

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project new-project-511709
```

Your Google account needs the **Dialogflow API Client** role (project owners already have it).

## 2. Configure the server

Copy `server/.env.example` to `server/.env` and set `AGENT_ID`.

The agent ID is in your browser's address bar when the agent is open in the console:

```
conversational-agents.cloud.google.com/projects/<PROJECT_ID>/locations/<LOCATION>/agents/<AGENT_ID>/...
```

## 3. Install and run

In two terminals from the project folder:

```bash
# Terminal 1: backend
npm run server:install
npm run server

# Terminal 2: Angular app
npm install
npm start
```

Open http://localhost:4200 and type `hi`.

## Project layout

| Path | What it is |
| --- | --- |
| `src/app/app.ts`, `app.html`, `app.scss` | Chat UI component |
| `src/app/chat.service.ts` | Calls `/api/chat`, keeps one session id per page load |
| `proxy.conf.json` | Sends `/api` calls from the dev server to port 3000 |
| `server/index.js` | Express server calling Dialogflow CX `detectIntent` |

## Troubleshooting

- **"Could not reach the server"**: the backend isn't running on port 3000.
- **`PERMISSION_DENIED`**: re-run the `gcloud auth application-default` commands, or check the IAM role.
- **`NOT_FOUND`**: `PROJECT_ID`, `LOCATION` or `AGENT_ID` in `server/.env` is wrong.
