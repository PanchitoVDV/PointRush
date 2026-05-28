import { useEffect, useState } from 'react';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { LoadingScreen, TeamPodium, TeamRankRow } from '../components/Ui';
import { formatPoints } from '../utils';

export default function TeamsPage() {
  const [teams, setTeams] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.teams()
      .then((d) => setTeams(d.teams ?? []))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingScreen />;

  const maxPoints = teams[0]?.points ?? 1;
  const totalPoints = teams.reduce((sum, t) => sum + (t.points ?? 0), 0);

  return (
    <div className="page teams-page">
      <header className="teams-page__header">
        <div>
          <p className="teams-page__eyebrow">CoreSMP · PointRush</p>
          <h1 className="teams-page__title">Team ranking</h1>
        </div>
        <div className="teams-page__stats">
          <div className="teams-page__stat">
            <span className="teams-page__stat-value">{teams.length}</span>
            <span className="teams-page__stat-label">Teams</span>
          </div>
          <div className="teams-page__stat">
            <span className="teams-page__stat-value">{formatPoints(totalPoints)}</span>
            <span className="teams-page__stat-label">Punten totaal</span>
          </div>
        </div>
      </header>

      {teams.length === 0 ? (
        <McPanel title="Geen teams">
          <p className="empty">Nog geen teams gevonden.</p>
        </McPanel>
      ) : (
        <>
          <TeamPodium teams={teams.slice(0, 3)} maxPoints={maxPoints} />

          <McPanel title="Alle teams">
            <p className="panel-desc">
              Live vanuit de server — zelfde data als <code>/points top</code>.
            </p>
            <div className="team-rank-list">
              <div className="team-rank-list__head">
                <span>#</span>
                <span>Team</span>
                <span>Leden</span>
                <span>Punten</span>
              </div>
              {teams.map((team, i) => (
                <TeamRankRow
                  key={team.id}
                  team={team}
                  rank={i + 1}
                  maxPoints={maxPoints}
                />
              ))}
            </div>
          </McPanel>
        </>
      )}
    </div>
  );
}
