const PLATFORM_META = {
  twitch: { name: 'Twitch', color: '#9146FF' },
  youtube: { name: 'YouTube', color: '#FF0000' },
  tiktok: { name: 'TikTok', color: '#FE2C55' },
};

function siteHosts() {
  const host = process.env.SITE_HOST ?? 'pointrush.coresmp.nl';
  return [...new Set([host, 'localhost', '127.0.0.1'])];
}

function twitchChannel(url) {
  const m = url?.match(/twitch\.tv\/([a-zA-Z0-9_]{3,25})/i);
  return m ? m[1].toLowerCase() : null;
}

function youtubeVideoId(url) {
  if (!url) return null;
  const live = url.match(/youtube\.com\/live\/([a-zA-Z0-9_-]{6,})/i);
  if (live) return live[1];
  const watch = url.match(/[?&]v=([a-zA-Z0-9_-]{6,})/i);
  if (watch) return watch[1];
  const short = url.match(/youtu\.be\/([a-zA-Z0-9_-]{6,})/i);
  if (short) return short[1];
  return null;
}

export function buildEmbed(platform, url) {
  switch (platform) {
    case 'twitch': {
      const channel = twitchChannel(url);
      if (!channel) return null;
      const parent = siteHosts().map((h) => `parent=${encodeURIComponent(h)}`).join('&');
      return {
        type: 'iframe',
        src: `https://player.twitch.tv/?channel=${encodeURIComponent(channel)}&${parent}&muted=true`,
      };
    }
    case 'youtube': {
      const id = youtubeVideoId(url);
      if (!id) return null;
      return {
        type: 'iframe',
        src: `https://www.youtube.com/embed/${encodeURIComponent(id)}?rel=0&modestbranding=1`,
      };
    }
    case 'tiktok':
      return { type: 'link', href: url };
    default:
      return null;
  }
}

export function formatLiveStream(doc) {
  const platform = doc.platform ?? 'twitch';
  const meta = PLATFORM_META[platform] ?? { name: platform, color: '#888' };
  const url = doc.url ?? '';
  return {
    playerId: doc._id,
    playerName: doc.playerName ?? '?',
    platform,
    platformName: meta.name,
    platformColor: meta.color,
    url,
    startedAt: doc.startedAt ?? 0,
    embed: buildEmbed(platform, url),
  };
}

export function demoLiveStreams() {
  const now = Date.now();
  return [
    formatLiveStream({
      _id: '00000000-0000-0000-0000-000000000001',
      playerName: 'Steve',
      platform: 'twitch',
      url: 'https://www.twitch.tv/demo',
      startedAt: now - 45 * 60 * 1000,
    }),
    formatLiveStream({
      _id: '00000000-0000-0000-0000-000000000003',
      playerName: 'Alex',
      platform: 'youtube',
      url: 'https://www.youtube.com/watch?v=jfKfPfyJRdk',
      startedAt: now - 20 * 60 * 1000,
    }),
  ];
}
