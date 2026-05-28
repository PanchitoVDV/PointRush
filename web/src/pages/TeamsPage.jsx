import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import McPanel from '../components/McPanel';
import { LoadingScreen, PlayerAvatar, TeamBadge } from '../components/Ui';
import { formatPoints, playerHead, rankMedal, teamColor } from '../utils';

export default function TeamsPage() {
  const [teams, setTeams] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.teams()
      .then((d) => setTeams(d.teams ?? []))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <LoadingScreen />;

  return (
    <div className="page teams-page">
      <McPanel title="Team Leaderboard" icon="🏆">
        <p className="panel-desc">Teams gerangschikt op totale punten — zelfde data als /points top in-game.</p>

        {teams.length === 0 ? (
          <p className="empty">Nog geen teams gevonden.</p>
        ) : (
          <div className="leaderboard">
            {teams.map((team, i) => (
              <div
                key={team.id}
                className={`leaderboard-row rank-${i + 1}`}
                style={{ '--team-color': teamColor(team.color) }}
              >
                <span className="lb-rank">{rankMedal(i + 1)}</span>
                <div className="lb-team">
                  <TeamBadge name={team.name} color={team.color} />
                  <span className="lb-members">{team.memberCount} speler(s)</span>
                </div>
                <div className="lb-members-heads">
                  {(team.members ?? []).slice(0, 4).map((uuid) => (
                    <Link key={uuid} to={`/players/${uuid}`} title="Speler profiel">
                      <PlayerAvatar uuid={uuid} size={32} />
                    </Link>
                  ))}
                </div>
                <span className="lb-points">{formatPoints(team.points)} pts</span>
              </div>
            ))}
          </div>
        )}
      </McPanel>

      <div className="podium-visual mc-panel">
        {teams.slice(0, 3).map((team, i) => (
          <div key={team.id} className={`podium-place place-${i + 1}`}>
            <img src={playerHead(team.leader, 48)} alt="" className="player-head" />
            <TeamBadge name={team.name} color={team.color} />
            <span>{formatPoints(team.points)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
