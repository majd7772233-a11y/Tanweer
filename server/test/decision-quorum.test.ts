import { describe, expect, it } from 'vitest';
import { countEligibleDecisionVotes, getRequiredDecisionVotes, hasDecisionQuorum, parseEligibleVoterSnapshot } from '../src/services/decisionQuorum';

describe('decision quorum policy', () => {
  it('requires three approvals for four eligible voters at 60 percent', () => {
    expect(getRequiredDecisionVotes(4, 60)).toBe(3);
    expect(hasDecisionQuorum({ votesFor: 1, votesAgainst: 0 }, 4, 60)).toBe(false);
    expect(hasDecisionQuorum({ votesFor: 3, votesAgainst: 1 }, 4, 60)).toBe(true);
  });
  it('keeps the two-vote minimum for groups with more than one eligible voter', () => {
    expect(getRequiredDecisionVotes(2, 10)).toBe(2);
    expect(getRequiredDecisionVotes(1, 60)).toBe(1);
  });
  it('counts only snapshot voters and ignores invalid choices', () => {
    const counts = countEligibleDecisionVotes([
      { user_id: 'member-a', vote_choice: 1 }, { user_id: 'member-b', vote_choice: 0 },
      { user_id: 'departed-member', vote_choice: 1 }, { user_id: 'member-c', vote_choice: 7 },
    ], ['member-a', 'member-b', 'member-c']);
    expect(counts).toEqual({ votesFor: 1, votesAgainst: 1 });
  });
  it('distinguishes missing legacy snapshots from explicitly empty snapshots', () => {
    expect(parseEligibleVoterSnapshot('legacy free text')).toBeNull();
    expect(parseEligibleVoterSnapshot(JSON.stringify({ voters: [] }))).toEqual([]);
    expect(countEligibleDecisionVotes([{ user_id: 'legacy-member', vote_choice: 1 }], null))
      .toEqual({ votesFor: 1, votesAgainst: 0 });
    expect(countEligibleDecisionVotes([{ user_id: 'legacy-member', vote_choice: 1 }], []))
      .toEqual({ votesFor: 0, votesAgainst: 0 });
  });
});
