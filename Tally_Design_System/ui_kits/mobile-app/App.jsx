const T2 = window.TallyDesignSystem_ae2e12;
const { AppBar: Bar, TabBar: Tabs, IconButton: IB, Toast: Snack } = T2;
const TABS = [
  { id: 'groups', label: 'Groups', icon: 'users' },
  { id: 'activity', label: 'Activity', icon: 'clock' },
  { id: 'friends', label: 'Friends', icon: 'user-round' },
  { id: 'you', label: 'You', icon: 'circle-user' },
];

function TallyApp() {
  const { GROUPS, MEMBERS } = window.TallyData;
  const [groups, setGroups] = React.useState(GROUPS);
  const [tab, setTab] = React.useState('groups');
  const [view, setView] = React.useState({ name: 'home' }); // home | overall | group
  const [sheet, setSheet] = React.useState(null);
  const [expense, setExpense] = React.useState(null);
  const [toast, setToast] = React.useState(null);

  const group = view.name === 'group' ? groups.find((g) => g.id === view.id) : null;

  const say = (message, tone = 'mint', icon = 'check') => {
    setToast({ message, tone, icon });
    clearTimeout(window.__tallyToast);
    window.__tallyToast = setTimeout(() => setToast(null), 2400);
  };

  const openGroup = (id) => { setTab('groups'); setView({ name: 'group', id }); };

  const back = () => {
    if (view.name === 'group') setView({ name: 'home' });
    else if (view.name === 'overall') setView({ name: 'home' });
  };

  const save = ({ title, total, cat, payer, splits }) => {
    const target = group || groups[0];
    const mine = (splits.find((s) => s.name === 'You') || { value: 0 }).value;
    const yourShare = payer === 'You' ? total - mine : -mine;
    setGroups((gs) => gs.map((g) => g.id !== target.id ? g : {
      ...g,
      balance: Number((g.balance + yourShare).toFixed(2)),
      expenses: [{ id: Date.now(), title, category: cat, paidBy: payer, date: 'Just now', day: 'Today', total, yourShare: Number(yourShare.toFixed(2)), splits }, ...g.expenses],
    }));
    setSheet(null);
    say('Split saved');
  };

  const settleAll = () => {
    const target = group || groups[0];
    setGroups((gs) => gs.map((g) => g.id !== target.id ? g : {
      ...g, balance: 0,
      balances: g.balances.map((b) => ({ ...b, amount: 0 })),
      expenses: g.expenses.map((e) => ({ ...e, settled: true, yourShare: 0 })),
    }));
    setSheet(null);
    say("You're all square", 'mint', 'party-popper');
  };

  const deleteExpense = () => {
    const id = expense.id;
    setGroups((gs) => gs.map((g) => ({ ...g, expenses: g.expenses.filter((e) => e.id !== id) })));
    setExpense(null);
    say('Expense deleted', 'ink', 'trash-2');
  };

  const title = group ? group.name
    : view.name === 'overall' ? 'Overall'
    : tab === 'groups' ? 'Tally' : tab === 'activity' ? 'Activity' : tab === 'friends' ? 'Friends' : 'You';
  const subtitle = group ? `${group.members.length} people · since ${group.started}`
    : view.name === 'overall' ? 'Every group, every person'
    : tab === 'groups' ? 'Split it, sorted' : null;

  return (
    <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--paper)', overflow: 'hidden' }}>
      <Bar
        title={title}
        subtitle={subtitle}
        onBack={view.name !== 'home' ? back : undefined}
        actions={group
          ? <><IB name="user-plus" size={42} label="Invite" onClick={() => say('Invite link copied', 'ink', 'link')} /><IB name="ellipsis" size={42} label="More" /></>
          : view.name === 'overall'
            ? <IB name="share-2" size={42} label="Share summary" onClick={() => say('Summary copied', 'ink', 'link')} />
            : <IB name="search" size={42} label="Search" />}
      />

      {group
        ? <GroupDetail group={group} onSettle={() => setSheet('settle')} onOpenExpense={setExpense} />
        : view.name === 'overall'
          ? <OverallScreen groups={groups} members={MEMBERS} onOpenGroup={openGroup} onSettle={() => { setView({ name: 'group', id: groups[0].id }); setSheet('settle'); }} />
          : tab === 'groups' ? <GroupsHome groups={groups} onOpen={openGroup} onOverall={() => setView({ name: 'overall' })} />
          : tab === 'activity' ? <Activity groups={groups} onOpenExpense={setExpense} />
          : <You members={MEMBERS} />}

      <Tabs tabs={TABS} value={view.name === 'home' ? tab : 'groups'}
        onChange={(id) => { setView({ name: 'home' }); setTab(id); }}
        centerAction={{ icon: 'plus', label: 'Add expense', onClick: () => { if (!group) setView({ name: 'group', id: groups[0].id }); setSheet('add'); } }} />

      <AddExpenseSheet open={sheet === 'add'} onClose={() => setSheet(null)} group={group || groups[0]} onSave={save} />
      <SettleUpSheet open={sheet === 'settle'} onClose={() => setSheet(null)} group={group || groups[0]} onDone={settleAll} />
      <ExpenseDetailSheet expense={expense} onClose={() => setExpense(null)} onDelete={deleteExpense} />
      <Snack open={!!toast} message={toast && toast.message} tone={toast && toast.tone} icon={toast && toast.icon} />
    </div>
  );
}

Object.assign(window, { TallyApp });
