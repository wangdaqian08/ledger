window.TallyData = (() => {
  const MEMBERS = [
    { name: 'You', hue: 6 },
    { name: 'Mei Wong', hue: 4 },
    { name: 'Sam Oyelaran', hue: 2 },
    { name: 'Ade Kim', hue: 5 },
  ];
  // splits: what each person's share of the expense total is
  const GROUPS = [
    {
      id: 'osaka', name: 'Osaka trip', icon: 'plane', hue: 5, members: MEMBERS, balance: -42.8,
      started: '12 Jun', currency: 'USD',
      expenses: [
        { id: 1, title: 'Sichuan Rose', category: 'food', paidBy: 'Mei Wong', date: 'Yesterday', day: 'Yesterday', total: 96.4, yourShare: -24.1,
          splits: [{ name: 'You', hue: 6, value: 24.1 }, { name: 'Mei Wong', hue: 4, value: 24.1 }, { name: 'Sam Oyelaran', hue: 2, value: 24.1 }, { name: 'Ade Kim', hue: 5, value: 24.1 }], note: 'Mapo tofu, twice-cooked pork, two beers' },
        { id: 2, title: 'Shinkansen tickets', category: 'transport', paidBy: 'You', date: 'Tue', day: 'Tuesday', total: 256.8, yourShare: 192.6,
          splits: [{ name: 'You', hue: 6, value: 64.2 }, { name: 'Mei Wong', hue: 4, value: 64.2 }, { name: 'Sam Oyelaran', hue: 2, value: 64.2 }, { name: 'Ade Kim', hue: 5, value: 64.2 }], note: 'Osaka → Kyoto, reserved seats' },
        { id: 3, title: 'Capsule hotel', category: 'stay', paidBy: 'Sam Oyelaran', date: 'Tue', day: 'Tuesday', total: 184, yourShare: -46,
          splits: [{ name: 'You', hue: 6, value: 46 }, { name: 'Mei Wong', hue: 4, value: 46 }, { name: 'Sam Oyelaran', hue: 2, value: 46 }, { name: 'Ade Kim', hue: 5, value: 46 }], note: 'Two nights, four pods' },
        { id: 4, title: 'Konbini run', category: 'groceries', paidBy: 'Ade Kim', date: 'Mon', day: 'Monday', total: 22.4, yourShare: -5.6,
          splits: [{ name: 'You', hue: 6, value: 5.6 }, { name: 'Mei Wong', hue: 4, value: 5.6 }, { name: 'Sam Oyelaran', hue: 2, value: 5.6 }, { name: 'Ade Kim', hue: 5, value: 5.6 }] },
        { id: 5, title: 'Karaoke', category: 'fun', paidBy: 'Mei Wong', date: 'Mon', day: 'Monday', total: 60, yourShare: -15,
          splits: [{ name: 'You', hue: 6, value: 15 }, { name: 'Mei Wong', hue: 4, value: 15 }, { name: 'Sam Oyelaran', hue: 2, value: 15 }, { name: 'Ade Kim', hue: 5, value: 15 }], note: 'Two hours, room 4' },
      ],
      balances: [
        { name: 'Mei Wong', hue: 4, amount: -39.1 },
        { name: 'Sam Oyelaran', hue: 2, amount: -46 },
        { name: 'Ade Kim', hue: 5, amount: 42.3 },
      ],
    },
    {
      id: 'flat', name: 'Flat 12B', icon: 'house', hue: 6, members: MEMBERS.slice(0, 3), balance: 0,
      started: '1 Jan', currency: 'USD',
      expenses: [
        { id: 6, title: 'Broadband', category: 'home', paidBy: 'You', date: '1 Jul', day: 'July', total: 45, yourShare: 0, settled: true,
          splits: [{ name: 'You', hue: 6, value: 15 }, { name: 'Mei Wong', hue: 4, value: 15 }, { name: 'Sam Oyelaran', hue: 2, value: 15 }] },
        { id: 7, title: 'Weekly shop', category: 'groceries', paidBy: 'Mei Wong', date: '28 Jun', day: 'June', total: 82.5, yourShare: 0, settled: true,
          splits: [{ name: 'You', hue: 6, value: 27.5 }, { name: 'Mei Wong', hue: 4, value: 27.5 }, { name: 'Sam Oyelaran', hue: 2, value: 27.5 }] },
      ],
      balances: [
        { name: 'Mei Wong', hue: 4, amount: 0 },
        { name: 'Sam Oyelaran', hue: 2, amount: 0 },
      ],
    },
    {
      id: 'brunch', name: 'Sunday brunch club', icon: 'coffee', hue: 2, members: MEMBERS, balance: 18.75,
      started: '3 Mar', currency: 'USD',
      expenses: [
        { id: 8, title: 'Eggs at Nine Lives', category: 'food', paidBy: 'You', date: 'Sun', day: 'Sunday', total: 75, yourShare: 18.75,
          splits: [{ name: 'You', hue: 6, value: 18.75 }, { name: 'Mei Wong', hue: 4, value: 18.75 }, { name: 'Sam Oyelaran', hue: 2, value: 18.75 }, { name: 'Ade Kim', hue: 5, value: 18.75 }], note: 'Big table, one bill' },
      ],
      balances: [
        { name: 'Mei Wong', hue: 4, amount: 6.25 },
        { name: 'Sam Oyelaran', hue: 2, amount: 6.25 },
        { name: 'Ade Kim', hue: 5, amount: 6.25 },
      ],
    },
  ];
  return { MEMBERS, GROUPS };
})();
