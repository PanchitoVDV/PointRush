import { useEffect, useState } from 'react';
import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import HomePage from './pages/HomePage';
import TeamsPage from './pages/TeamsPage';
import EventsPage from './pages/EventsPage';
import EventDetailPage from './pages/EventDetailPage';
import PlayersPage from './pages/PlayersPage';
import PlayerDetailPage from './pages/PlayerDetailPage';
import { api } from './api/client';

export default function App() {
  const [demo, setDemo] = useState(false);

  useEffect(() => {
    api.meta().then((d) => setDemo(!!d.demo)).catch(() => setDemo(true));
  }, []);

  return (
    <Routes>
      <Route element={<Layout demo={demo} />}>
        <Route index element={<HomePage />} />
        <Route path="teams" element={<TeamsPage />} />
        <Route path="events" element={<EventsPage />} />
        <Route path="events/:id" element={<EventDetailPage />} />
        <Route path="players" element={<PlayersPage />} />
        <Route path="players/:uuid" element={<PlayerDetailPage />} />
      </Route>
    </Routes>
  );
}
