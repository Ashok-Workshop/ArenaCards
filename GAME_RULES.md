# ArenaCards classic-style practice rules

This is an original ArenaCards rule specification. It does not use another game's branding or artwork.

## Deck and seats

- 108 cards: four colors; one zero and two of each number 1-9, Skip, Reverse, and Draw Two per color; four Wild and four Wild Draw Four.
- Practice supports two, three, or four seats. The human player is seat 0; remaining seats are CPU opponents.
- Each seat starts with seven cards. The opening discard is a number card.

## A turn

- Play a card matching the active color or top-card value, or play a Wild.
- A Wild player selects the next active color.
- If unable to play, draw one card. If the drawn card is playable, the player may play that card; otherwise, or if choosing not to play it, end the turn. The drawn card is the only card playable that turn.
- If no card is available to draw, the player may still play a matching card already held, or end the turn.
- Draw penalties do not stack. A player receiving Draw Two or Wild Draw Four draws the penalty and loses their turn.

## Action cards

- Skip: the next seat loses its turn.
- Reverse: direction changes. In a two-seat match it also skips the other seat, so the player who played Reverse takes the next turn.
- Draw Two: the next seat draws up to two available cards and loses its turn.
- Wild Draw Four: it is legal only when the player has no card matching the active color. A player may attempt an illegal play, but the next player may challenge and the stored pre-play hand determines the result.
  - Successful challenge: the player who played Wild Draw Four draws up to four cards; the challenger keeps their turn.
  - Failed challenge: the challenger draws up to six cards and loses their turn.
  - No challenge: the next player draws up to four cards and loses their turn.
- Draw penalties are capped by the cards actually available in the draw pile and recyclable discard pile.
- When a play leaves exactly one card, the player must announce UNO within ten seconds. Another participant may catch a missed announcement before the window expires; the offender then draws up to two available cards. In online matches, the window also closes when the next player takes a normal turn action.
- In local practice, CPU opponents announce automatically. A human player's unannounced call is caught and penalized when the ten-second window expires; play resumes after the announcement or penalty.
- In online matches, UNO calls and catches are validated by Cloud Functions using server time. Public match state exposes only the pending player's identity and deadline; hands remain private.

## Round completion and scoring

- Emptying a hand ends the round after any pending Wild Draw Four challenge is resolved and any applicable penalty is applied.
- A completed hand scores its remaining number cards at face value, action cards at 20, and Wild cards at 50. Current UI displays remaining hand score; cumulative multi-round scoring is not implemented.
- If no cards can be drawn and every seat passes without a playable card, the round ends in a draw.

## CPU information boundary

CPU move and challenge decisions use the CPU's own hand, public top-card/active-color state, public opponent card counts, selected difficulty, and random input. CPU decisions do not inspect another player's hidden cards to select or challenge a move. A challenge result reveals whether the Wild Draw Four was legal only after the challenger commits.

## Not included yet

- Stacking draw cards is disabled.
- Match-wide score targets, cumulative match points, house-rule configuration, and a challenge explanation/tutorial remain future work.
