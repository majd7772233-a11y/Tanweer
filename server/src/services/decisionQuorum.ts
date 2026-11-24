export interface DecisionVoteRow {
  decision_id?: string;
  user_id: string;
  vote_choice: number;
}
export interface DecisionVoteCounts { votesFor: number; votesAgainst: number; }

/** Null means a legacy decision without a snapshot; [] is explicitly empty. */
export function parseEligibleVoterSnapshot(description: string | null | undefined): string[] | null {
  if (!description) return null;
  try {
    const parsed = JSON.parse(description) as { voters?: unknown };
    if (!Array.isArray(parsed?.voters)) return null;
    return Array.from(new Set(parsed.voters.filter((value): value is string =>
      typeof value === 'string' && value.trim().length > 0
    )));
  } catch { return null; }
}
export function countEligibleDecisionVotes(rows: DecisionVoteRow[], eligibleVoterIds: string[] | null): DecisionVoteCounts {
  const eligible = eligibleVoterIds === null ? null : new Set(eligibleVoterIds);
  return rows.reduce<DecisionVoteCounts>((counts, row) => {
    if (eligible && !eligible.has(row.user_id)) return counts;
    if (row.vote_choice === 1) counts.votesFor += 1;
    else if (row.vote_choice === 0) counts.votesAgainst += 1;
    return counts;
  }, { votesFor: 0, votesAgainst: 0 });
}
export function getRequiredDecisionVotes(totalEligibleVoters: number, thresholdPercent: number): number {
  const total = Number.isFinite(totalEligibleVoters) ? Math.max(1, Math.floor(totalEligibleVoters)) : 1;
  const percent = Number.isFinite(thresholdPercent) ? Math.min(100, Math.max(1, thresholdPercent)) : 50;
  const threshold = Math.ceil(total * percent / 100);
  return total > 1 ? Math.max(2, threshold) : 1;
}
export function hasDecisionQuorum(counts: DecisionVoteCounts, totalEligibleVoters: number, thresholdPercent: number): boolean {
  const required = getRequiredDecisionVotes(totalEligibleVoters, thresholdPercent);
  return counts.votesFor >= required && counts.votesFor > counts.votesAgainst;
}
