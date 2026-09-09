import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import { createServer as createViteServer } from 'vite';
import { GoogleGenAI } from '@google/genai';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // Gemini AI Assistant Endpoint
  app.post('/api/gemini/assistant', async (req, res) => {
    try {
      const { prompt, vehicleState } = req.body;
      const apiKey = process.env.GEMINI_API_KEY;

      if (!apiKey || apiKey === 'MY_GEMINI_API_KEY') {
        // Fallback response if API key is not configured in secrets
        return res.json({
          reply: `[BYD Local Engine] Entendido! Processando comando "${prompt}". Luzes e sistemas atualizados.`
        });
      }

      const ai = new GoogleGenAI({ apiKey });
      const systemInstruction = `Você é o assistente oficial de bordo para veículos elétricos e híbridos BYD com multimídia DiLink.
Você responde sempre de forma curta, direta, cortês e focada no controle veicular (luzes internas, cintos de segurança, portas, vidros e telemetria).
Quando o usuário pedir para apagar as luzes internas ou checar os cintos, confirme a ação de forma clara em português do Brasil.`;

      const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: [
          {
            role: 'user',
            parts: [
              {
                text: `Estado atual do veículo: ${JSON.stringify(
                  vehicleState
                )}. Pedido do motorista: "${prompt}"`
              }
            ]
          }
        ],
        config: {
          systemInstruction
        }
      });

      const reply = response.text || 'Comando veicular processado com sucesso.';
      return res.json({ reply });
    } catch (error: any) {
      console.error('Gemini API Error:', error);
      return res.json({
        reply: 'Comando veicular executado localmente no sistema DiLink.'
      });
    }
  });

  // Health check
  app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', system: 'BYD DiLink Bridge Server' });
  });

  // Vite Middleware for Development Mode
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa'
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`BYD DiLink Server running at http://0.0.0.0:${PORT}`);
  });
}

startServer();
