import express from 'express';
import cors from 'cors';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import api from './routes/api.js';
import { connectDb } from './db.js';

dotenv.config();

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const port = parseInt(process.env.PORT ?? '3847', 10);
const host = process.env.HOST ?? '0.0.0.0';

app.use(cors());
app.use(express.json());
app.use('/api', api);

const distPath = path.join(__dirname, '..', 'dist');
app.use(express.static(distPath));
app.get('*', (req, res, next) => {
  if (req.path.startsWith('/api')) return next();
  res.sendFile(path.join(distPath, 'index.html'), (err) => {
    if (err) next();
  });
});

connectDb()
  .then((d) => {
    if (d) console.log('MongoDB verbonden');
    else console.warn('MongoDB niet bereikbaar — demo data wordt gebruikt');
  })
  .catch(() => console.warn('MongoDB niet bereikbaar — demo data wordt gebruikt'));

app.listen(port, host, () => {
  console.log(`PointRush stats site: http://${host}:${port}`);
});
