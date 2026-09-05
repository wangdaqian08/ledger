/**
 * English strings. The source language: every key is written here first.
 *
 * Keys are named for what the string *means*, not where it appears, so moving a label between
 * screens does not orphan a translation.
 */
export default {
  common: {
    all: 'All',
    back: 'Back',
    cancel: 'Cancel',
    close: 'Close',
    done: 'Done for now',
    save: 'Save',
    settled: 'All square',
    you: 'You',
    undo: 'Undo',
  },
  money: {
    youAreOwed: 'You are owed',
    youOwe: 'You owe',
    allSquare: 'All square',
  },
  settle: {
    pay: 'Pay',
    remind: 'Remind',
    // Tally sends nothing — push is out of phase 1 — so the tapped state must not claim it did.
    // It turns into an honest instruction rather than a "delivered ✓" that never happened.
    reminded: 'Nudge them yourself',
    sentForConfirmation: 'Sent to {name} for confirmation',
    awaitingYou: '{name} says they paid you',
    settled: 'Settled',
    youPaidThem: 'You paid {name}',
    theyPaidYou: '{name} paid you',
    declinedByThem: '{name} declined your payment',
    undoConfirm: 'Undo this settled payment? Their balance will re-open.',
    owesYou: '{name} owes you',
    youOweThem: 'You owe {name}',
    owesYouShort: 'Owes you',
    youOweShort: 'You owe',
    waiting: 'Waiting for confirmation',
    approve: 'Approve',
    reject: 'Decline',
    // Families (§7b): a complete, ephemeral partition of the trip built right on this screen —
    // never called a "group", which this app already uses for a whole trip.
    byPerson: 'By person',
    byFamily: 'By family',
    noFamiliesYet: 'No families built yet.',
    buildFamily: 'Build a family',
    addFamily: 'Add family',
    chooseFamily: 'Choose who is in this family',
    familyCannotBeEveryone: "Leave at least one person out — a first family can't be everyone",
    // Pronoun-free: a Family is never "you", so these drop the "you"/"them" BalanceRow can use.
    familyOwesCardSingular: '{other} owes {card}',
    familyOwesCardPlural: '{other} owe {card}',
    familyCardOwesSingular: '{card} owes {other}',
    familyCardOwesPlural: '{card} owe {other}',
    familyNetOwed: 'This family is owed',
    familyNetOwes: 'This family owes',
  },
  payback: {
    pending: 'Waiting for {name}',
    approved: 'Confirmed',
    rejected: 'Needs another look',
  },
  signin: {
    title: 'Tally',
    tagline: 'Split it, sorted',
    namePlaceholder: 'Your name',
    button: 'Sign in',
    failed: 'Couldn’t sign in — check your connection and try again.',
    note: 'Your name is the whole sign-in — no password. Whoever types it is you, so keep it to friends.',
  },
  trips: {
    title: 'Your groups',
    newGroup: 'New group',
    empty: 'No groups yet',
    emptyBody: 'Make one and share the link — everyone picks their own name.',
    groupsSettled: '{count} of {total} settled',
    signOut: 'Sign out',
    name: 'Group name',
    currency: 'Currency',
    create: 'Create group',
    people: '{count} people',
    personOne: '{count} person',
    completed: 'Completed',
    showHidden: 'Show put away ({count})',
    hideHidden: 'Hide them again',
    recentlyDeleted: 'Recently deleted',
    // "Restorable until", not "deleted on": the sweep runs nightly, so destruction lands some
    // hours after the date — promising the thing that is true right up to the deadline.
    restorableUntil: 'Restorable until {date}',
    restore: 'Restore',
    liveEmpty: 'Nothing on right now — your finished groups are below.',
    loadFailed: 'Couldn’t load your groups. Check your connection and try again.',
    retry: 'Try again',
  },
  trip: {
    groupSpend: 'Group spend',
    yourShare: 'Your share',
    youFronted: 'You fronted',
    whoOwesWho: 'Who owes who',
    expenses: 'Expenses',
    filterAll: 'All',
    filterUnsettled: 'Unsettled',
    filterYouPaid: 'You paid',
    empty: 'Nothing spent yet',
    emptyBody: 'Add the first expense with the + button.',
    // The one entry to the roster *and* the trip's own controls (end, put away, delete), so it is
    // named for both — parking "end trip" behind a bare "Invite" hid it from the person who needs it.
    invite: 'People & settings',
    linkCopied: 'Link copied — anyone with it can pick their name',
    settleUp: 'Settle up',
    addExpense: 'Add expense',
    exportCsv: 'Export CSV',
    paidBy: '{name} paid',
    youPaid: 'You paid',
    settledCaption: 'settled',
    shareCaption: 'your share',
    frontedCaption: 'you fronted',
    oweCaption: 'you owe',
    getCaption: 'you get',
    notFound: 'This trip is not here',
    notFoundBody: "It may have been removed, or you're not on it.",
    remindFailed: 'Couldn’t do that just now.',
    loadFailed: 'Couldn’t load this trip. Check your connection and try again.',
    retry: 'Try again',
    ended: 'Ended',
  },
  receipt: {
    section: 'Receipt',
    add: 'Add receipt',
    alt: 'Receipt photo',
    replace: 'Replace',
    remove: 'Remove',
    removeConfirm: 'Remove this receipt photo?',
  },
  // Its own namespace rather than a corner of addExpense: the same field is offered when a bill is
  // added and when it is corrected, and a key named for one screen would read as a lie on the other.
  comment: {
    add: 'Add a comment (optional)',
    // The way into an existing comment is the comment itself, so the tap target needs a name of
    // its own: without one a screen reader announces the words and then "button", leaving what the
    // button would do to guesswork.
    edit: 'Edit comment',
    placeholder: 'Add a comment...',
    count: '{count}/{max} words',
    // Chinese writes no spaces, so a comment of any length counts as one word and the word counter
    // never moves — while the 1200-character backstop is what actually stops the typing, silently.
    // Whichever limit the text will reach first is the one counted, so the wall can be seen coming.
    countChars: '{count}/{max} characters',
    // Saving an emptied box deletes the comment, so the two ways out of the editor are named for
    // what they do rather than left as a matching pair of buttons.
    save: 'Save comment',
    discard: 'Discard changes',
    removeConfirm: 'Save an empty comment? This removes it from the expense.',
  },
  addExpense: {
    howMuch: 'How much?',
    whatWasIt: 'What was it?',
    titlePlaceholder: 'Dinner at Sichuan Rose',
    // A blank title falls back to this, never to the example placeholder — an untitled expense
    // should read "Expense", not name a restaurant nobody went to.
    untitled: 'Expense',
    when: 'When',
    next: 'Next',
    whoSplitIt: 'Who split it?',
    whoPaid: 'Who paid',
    splitBetween: 'Split between',
    all: 'Everyone',
    how: 'How',
    evenly: 'Evenly',
    custom: 'Custom',
    each: '{amount} each',
    save: 'Save expense',
    savedPhotoFailed: 'Expense saved — the photo didn’t upload: {reason}. Press Save to try the photo again.',
    discardConfirm: 'Discard this expense?',
    back: 'Back',
  },
  itemDetail: {
    total: 'Total',
    yourShare: 'You owe',
    yourPortion: 'Your share',
    paidBy: 'Paid by',
    frontedTheBill: 'fronted the whole bill',
    howSplit: 'How it was split · {count} people',
    splitEqual: 'Even split',
    splitWeighted: 'By shares',
    splitExact: 'Exact amounts',
    waitingOn: 'Waiting on you',
    paybacks: 'Paid back so far',
    noPaybacks: 'Nobody has paid anything back yet',
    approve: 'Yes, paid me',
    reject: 'Not yet',
    rejectReason: 'Say why, so it can be put right',
    payBack: 'Pay this back',
    undoConfirm: 'Undo this confirmed repayment? The balance will re-open.',
    delete: 'Delete expense',
    deleteConfirm: 'Delete this expense? Its repayment claims go with it.',
    deleteApprovedConfirm:
      '{count} confirmed repayment(s) totalling {amount} will be erased with this bill, and the balances will move. Really delete?',
  },
  claim: {
    title: 'Pay back',
    amount: 'How much did you pay?',
    date: 'When',
    note: 'Note',
    notePlaceholder: 'Bank transfer',
    send: 'Send for confirmation',
    disclaimer: "Tally doesn't move money. This asks {name} to confirm cash really changed hands.",
  },
  editSplit: {
    title: 'Fix the bill',
    amount: 'How much was it?',
    save: 'Save the bill',
  },
  invite: {
    title: 'People & settings',
    claimed: 'On the trip',
    unclaimed: 'Not claimed yet',
    namePlaceholder: 'Add a name',
    add: 'Add',
    copyLink: 'Copy invite link',
    rename: 'Rename {name}',
    endTrip: 'End trip',
    reopenTrip: 'Reopen trip',
    endConfirm:
      'End this trip? New expenses stop, settling up continues, and receipt photos are deleted 14 days on.',
    endNote: 'Ending stops new expenses. Settling up stays open, and receipt photos are kept for 14 days.',
    endedNote: 'This trip has ended. Reopen it to change expenses again.',
    putAway: 'Put away',
    putBack: 'Put back on the list',
    putAwayNote:
      'Takes this trip off everyone’s home screen. It still opens by its link, and settling up carries on.',
    putBackNote: 'This trip is off everyone’s home screen. Anyone can still find it under Completed.',
    // Shown disabled on a live trip so the control is discoverable, with the reason it is not yet usable.
    putAwayLocked: 'Put away',
    putAwayLockedNote: 'End the trip first — only finished trips can be put away.',
    deleteTrip: 'Delete trip',
    deleteNote: 'Deletes it for everyone. You can restore it from your home screen for 30 days.',
    deleteConfirm: 'Delete {name} for everyone? You can restore it for 30 days.',
    // Naming the money is the whole point of the second wording: this is the last moment a
    // warning can still change the outcome.
    deleteConfirmOutstanding:
      'Delete {name} for everyone? This group still has {amount} unsettled. You can restore it for 30 days.',
  },
  join: {
    title: 'Join {trip}',
    pickYourName: 'Pick your name',
    claim: "That's me",
    signInFirst: 'Sign in first, then pick your name.',
    allClaimed: 'Every name on this trip is already taken.',
    badLink: 'This link is not valid any more — ask for a fresh one.',
    alreadyOn: "You're already on this trip as {name}",
    openTrip: 'Open {trip}',
    signedInAs: 'Signed in as {name}',
    notYou: 'Not you? Sign out',
  },
} as const
