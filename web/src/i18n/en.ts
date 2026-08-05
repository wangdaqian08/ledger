/**
 * English strings. The source language: every key is written here first.
 *
 * Keys are named for what the string *means*, not where it appears, so moving a label between
 * screens does not orphan a translation.
 */
export default {
  common: {
    all: 'All',
    cancel: 'Cancel',
    done: 'Done for now',
    save: 'Save',
    settled: 'All square',
  },
  money: {
    youAreOwed: 'You are owed',
    youOwe: 'You owe',
    allSquare: 'All square',
  },
  settle: {
    pay: 'Pay',
    remind: 'Remind',
    sentForConfirmation: 'Sent to {name} for confirmation',
    owesYou: '{name} owes you',
    youOweThem: 'You owe {name}',
  },
  payback: {
    pending: 'Waiting for {name}',
    approved: 'Confirmed',
    rejected: 'Needs another look',
  },
} as const
