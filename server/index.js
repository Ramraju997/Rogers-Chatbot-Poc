// Minimal backend that forwards chat messages to a Dialogflow CX
// (Conversational Agents) agent and returns its text replies.
require('dotenv').config();

const express = require('express');
const cors = require('cors');
const { SessionsClient } = require('@google-cloud/dialogflow-cx');

const {
  PROJECT_ID,
  LOCATION = 'global',
  AGENT_ID,
  LANGUAGE_CODE = 'en',
  PORT = 3000,
} = process.env;

if (!PROJECT_ID || !AGENT_ID || AGENT_ID === 'paste-your-agent-id-here') {
  console.error('Missing PROJECT_ID or AGENT_ID. Copy server/.env.example to server/.env and fill it in.');
  process.exit(1);
}

// Global agents use the default endpoint; regional agents need a regional one.
const apiEndpoint =
  LOCATION === 'global' ? 'dialogflow.googleapis.com' : `${LOCATION}-dialogflow.googleapis.com`;
const client = new SessionsClient({ apiEndpoint });

const app = express();
app.use(cors());
app.use(express.json({ limit: '10kb' }));

app.get('/api/health', (_req, res) => res.json({ ok: true }));

app.post('/api/chat', async (req, res) => {
  const { sessionId, text } = req.body ?? {};

  if (typeof text !== 'string' || !text.trim()) {
    return res.status(400).json({ error: 'Message text is required.' });
  }
  if (typeof sessionId !== 'string' || !/^[\w-]{1,36}$/.test(sessionId)) {
    return res.status(400).json({ error: 'A valid sessionId is required.' });
  }

  const session = client.projectLocationAgentSessionPath(PROJECT_ID, LOCATION, AGENT_ID, sessionId);

  try {
    const [response] = await client.detectIntent({
      session,
      queryInput: {
        text: { text: text.trim().slice(0, 256) },
        languageCode: LANGUAGE_CODE,
      },
    });

    const messages = (response.queryResult?.responseMessages ?? [])
      .flatMap((m) => m.text?.text ?? [])
      .filter(Boolean);

    res.json({ messages });
  } catch (err) {
    console.error('Dialogflow error:', err.message);
    res.status(502).json({ error: `Dialogflow error: ${err.details || err.message}` });
  }
});

app.listen(PORT, () => {
  console.log(`Chat server on http://localhost:${PORT} → agent ${AGENT_ID} (${LOCATION})`);
});
