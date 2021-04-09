// https://stackoverflow.com/a/7616484/1064115
export function hashCode(s: string): string {
  let hash = 0;
  if (s.length == 0) return hash.toString();
  for (let i = 0; i < s.length; i++) {
    hash = (hash << 5) - hash + s.charCodeAt(i);
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash.toString();
}

export function getHashedUrl(): string {
  if ((window as any).hashedUrl) {
    return (window as any).hashedUrl;
  }
  const hash = hashCode(window.location.toString());
  (window as any).hashedUrl = hash;
  return hash;
}
