const T = window.TallyDesignSystem_ae2e12;
const { Button, IconButton, Card, Chip, Badge, Avatar, AvatarStack, Amount, Icon } = T;
const { AppBar, TabBar, Sheet } = T;
const { GroupCard, ExpenseRow, BalanceRow, ListRow } = T;
const { SplitBar, PersonToggleRow, Stepper, Input, AmountInput, Keypad, CategoryPicker } = T;
const { Toast, ProgressBar, EmptyState, SettledBanner } = T;

const Screen = ({ children }) => (
  <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', WebkitOverflowScrolling: 'touch' }}>{children}</div>
);

const Pad = ({ children }) => (
  <div style={{ padding: '0 var(--gutter-screen) var(--space-10)', display: 'flex', flexDirection: 'column', gap: 'var(--stack-section)' }}>{children}</div>
);

const SectionLabel = ({ children, right }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, margin: '0 0 8px', minHeight: 26 }}>
    <span style={{ font: '800 12px/1 var(--font-core)', letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--text-subtle)' }}>{children}</span>
    {right}
  </div>
);

/* Small sunk stat tile used in the stats strips. */
const Stat = ({ label, children, tone }) => (
  <div style={{
    flex: 1, minWidth: 0, padding: '10px 12px', background: 'var(--bg-sunk)',
    border: '2px solid var(--ink)', borderRadius: 'var(--radius-md)',
  }}>
    <div style={{ font: '800 11px/1 var(--font-core)', letterSpacing: '0.05em', textTransform: 'uppercase', color: 'var(--text-subtle)' }}>{label}</div>
    <div style={{ marginTop: 6 }}>{children}</div>
  </div>
);

const CAT_LABEL = { food: 'Food', drinks: 'Drinks', transport: 'Transport', stay: 'Stay', groceries: 'Groceries', fun: 'Fun', home: 'Home', other: 'Other' };
const CAT_HUE = { food: 2, drinks: 3, transport: 5, stay: 7, groceries: 4, fun: 8, home: 6, other: 1 };
const CAT_ICON = { food: 'utensils', drinks: 'beer', transport: 'car-front', stay: 'bed-double', groceries: 'shopping-basket', fun: 'ticket', home: 'house', other: 'circle-dashed' };

const CatDisc = ({ category, size = 44, radius = 'var(--radius-md)' }) => {
  const hue = CAT_HUE[category] || 1;
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      width: size, height: size, flex: '0 0 auto', background: `var(--person-${hue})`,
      border: '2px solid var(--ink)', borderRadius: radius,
      color: hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
    }}>
      <Icon name={CAT_ICON[category] || 'circle-dashed'} size={Math.round(size * 0.5)} />
    </span>
  );
};

/* ---------------- Groups home ---------------- */
function GroupsHome({ groups, onOpen, onOverall }) {
  const net = groups.reduce((a, g) => a + g.balance, 0);
  return (
    <Screen>
      <Pad>
        <div>
          <Card tone={net >= 0 ? 'mint' : 'coral'} lift={6} pressable onClick={onOverall}>
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
              <div style={{ font: '800 12px/1 var(--font-core)', letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--ink-3)' }}>
                {net >= 0 ? 'Overall you get back' : 'Overall you owe'}
              </div>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2, font: '800 12px/1 var(--font-core)', color: 'var(--action)' }}>
                Details <Icon name="chevron-right" size={14} />
              </span>
            </div>
            <div style={{ marginTop: 6 }}><Amount value={net} size="hero" tone={net >= 0 ? 'owed' : 'owe'} /></div>
            <div style={{ marginTop: 'var(--space-3)' }}>
              <ProgressBar value={groups.filter((g) => g.balance === 0).length} max={groups.length} tone={net >= 0 ? 'mint' : 'coral'} label="Groups settled" height={12} />
            </div>
          </Card>
        </div>
        <div>
          <SectionLabel right={<Chip size="sm" icon="plus">New group</Chip>}>Your groups</SectionLabel>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--gap-list)' }}>
            {groups.map((g) => (
              <GroupCard key={g.id} name={g.name} icon={g.icon} hue={g.hue} members={g.members} balance={g.balance} onClick={() => onOpen(g.id)} />
            ))}
          </div>
        </div>
      </Pad>
    </Screen>
  );
}

/* ---------------- Overall balance detail ---------------- */
function OverallScreen({ groups, members, onOpenGroup, onSettle }) {
  const net = groups.reduce((a, g) => a + g.balance, 0);
  const totalSpent = groups.reduce((a, g) => a + g.expenses.reduce((b, e) => b + e.total, 0), 0);
  const yourOutlay = groups.reduce((a, g) => a + g.expenses.filter((e) => e.paidBy === 'You').reduce((b, e) => b + e.total, 0), 0);

  // Net per person across every group.
  const perPerson = {};
  groups.forEach((g) => g.balances.forEach((b) => {
    perPerson[b.name] = perPerson[b.name] || { name: b.name, hue: b.hue, amount: 0, groups: [] };
    perPerson[b.name].amount += b.amount;
    if (b.amount !== 0) perPerson[b.name].groups.push(g.name);
  }));
  const people = Object.values(perPerson).sort((a, b) => a.amount - b.amount);
  const owedTo = people.filter((p) => p.amount < 0);
  const owedBy = people.filter((p) => p.amount > 0);

  return (
    <Screen>
      <Pad>
        <div>
          <Card tone={net >= 0 ? 'mint' : 'coral'} lift={6}>
            <SectionLabel>{net >= 0 ? 'Overall you get back' : 'Overall you owe'}</SectionLabel>
            <Amount value={net} size="hero" tone={net >= 0 ? 'owed' : 'owe'} />
            <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-4)' }}>
              <Stat label="You owe"><Amount value={owedTo.reduce((a, p) => a + p.amount, 0)} tone="owe" size="md" /></Stat>
              <Stat label="Owed to you"><Amount value={owedBy.reduce((a, p) => a + p.amount, 0)} tone="owed" size="md" /></Stat>
            </div>
          </Card>
        </div>

        <div>
          <SectionLabel right={<Badge tone="coral">{`${owedTo.length + owedBy.length} open`}</Badge>}>Across everyone</SectionLabel>
          {owedTo.length + owedBy.length === 0
            ? <SettledBanner message="You're all square" sub="Every group, everyone. Nice." />
            : <Card padded={false} lift={2}>
                {[...owedTo, ...owedBy].map((p, i) => (
                  <div key={p.name}>
                    <BalanceRow name={p.name} hue={p.hue} amount={p.amount} onSettle={onSettle} size="md"
                      divider={false} style={{ paddingBottom: 4 }} />
                    <div style={{
                      padding: '0 var(--pad-card) 12px 68px', fontSize: 'var(--text-caption)', color: 'var(--text-subtle)',
                      borderBottom: i < owedTo.length + owedBy.length - 1 ? '1.5px solid var(--border-soft)' : 'none',
                    }}>{p.groups.join(' · ')}</div>
                  </div>
                ))}
              </Card>}
        </div>

        <div>
          <SectionLabel>By group</SectionLabel>
          <Card padded={false} lift={2}>
            {groups.map((g, i) => (
              <ListRow key={g.id} onClick={() => onOpenGroup(g.id)} chevron
                divider={i < groups.length - 1}
                leading={<CatDisc category="other" size={36} />}
                title={g.name}
                subtitle={`${g.expenses.length} expenses · since ${g.started}`}
                trailing={g.balance === 0
                  ? <Badge tone="mint">Square</Badge>
                  : <Amount value={g.balance} tone={g.balance > 0 ? 'owed' : 'owe'} showSign />} />
            ))}
          </Card>
        </div>

        <div>
          <SectionLabel>Your share of the spending</SectionLabel>
          <Card lift={2}>
            <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <Stat label="Group total"><Amount value={totalSpent} size="md" /></Stat>
              <Stat label="You fronted"><Amount value={yourOutlay} size="md" /></Stat>
            </div>
            <div style={{ marginTop: 'var(--space-4)' }}>
              <ProgressBar value={yourOutlay} max={totalSpent} tone="action" label="Fronted by you" />
            </div>
            <p style={{ margin: '12px 0 0', fontSize: 'var(--text-caption)', color: 'var(--text-muted)' }}>
              Across {groups.length} groups and {members.length} people. Tally never moves money — it just keeps the maths straight.
            </p>
          </Card>
        </div>
      </Pad>
    </Screen>
  );
}

/* ---------------- Group detail ---------------- */
function GroupDetail({ group, onSettle, onOpenExpense }) {
  const [filter, setFilter] = React.useState('all');
  const settled = group.balance === 0;
  const total = group.expenses.reduce((a, e) => a + e.total, 0);
  const yours = group.expenses.reduce((a, e) => a + (e.splits ? (e.splits.find((s) => s.name === 'You') || {}).value || 0 : 0), 0);
  const youFronted = group.expenses.filter((e) => e.paidBy === 'You').reduce((a, e) => a + e.total, 0);

  const list = group.expenses.filter((e) =>
    filter === 'all' ? true : filter === 'open' ? !e.settled : e.paidBy === 'You');

  const days = [];
  list.forEach((e) => {
    const last = days[days.length - 1];
    if (last && last.day === e.day) last.items.push(e);
    else days.push({ day: e.day, items: [e] });
  });

  return (
    <Screen>
      <Pad>
        <div>
          {settled
            ? <SettledBanner message="You're all square" sub="Nobody owes anybody. Nice." />
            : <Card tone={group.balance > 0 ? 'mint' : 'coral'} lift={6}>
                <SectionLabel>{group.balance > 0 ? 'You get back' : 'You owe'}</SectionLabel>
                <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 12 }}>
                  <Amount value={group.balance} size="hero" tone={group.balance > 0 ? 'owed' : 'owe'} />
                  <Button size="sm" variant="solid" tone={group.balance > 0 ? 'action' : 'mint'} icon="handshake" onClick={onSettle}>Settle up</Button>
                </div>
              </Card>}
          <div style={{ display: 'flex', gap: 'var(--space-2)', marginTop: 'var(--space-3)' }}>
            <Stat label="Group spend"><Amount value={total} size="md" /></Stat>
            <Stat label="Your share"><Amount value={yours} size="md" /></Stat>
            <Stat label="You fronted"><Amount value={youFronted} size="md" /></Stat>
          </div>
        </div>

        <div>
          <SectionLabel right={<Chip size="sm" icon="user-plus">Invite</Chip>}>Who owes who</SectionLabel>
          <Card padded={false} lift={2}>
            {group.balances.map((b, i) => (
              <BalanceRow key={b.name} name={b.name} hue={b.hue} amount={b.amount} divider={i < group.balances.length - 1} />
            ))}
          </Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 10 }}>
            <AvatarStack people={group.members} size={28} max={5} />
            <span style={{ fontSize: 'var(--text-caption)', color: 'var(--text-muted)' }}>
              {group.members.length} people · {group.currency} · since {group.started}
            </span>
          </div>
        </div>

        <div>
          <SectionLabel right={<Badge>{`${list.length} of ${group.expenses.length}`}</Badge>}>Expenses</SectionLabel>
          <div style={{ display: 'flex', gap: 'var(--gap-inline)', overflowX: 'auto', paddingBottom: 8 }}>
            <Chip size="sm" selected={filter === 'all'} onClick={() => setFilter('all')}>All</Chip>
            <Chip size="sm" selected={filter === 'open'} onClick={() => setFilter('open')}>Unsettled</Chip>
            <Chip size="sm" selected={filter === 'mine'} onClick={() => setFilter('mine')}>You paid</Chip>
          </div>
          {list.length === 0
            ? <EmptyState icon="receipt" title="Nothing to show" body="No expenses match that filter yet." />
            : <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
                {days.map((d) => (
                  <div key={d.day}>
                    <div style={{ font: '800 11px/1 var(--font-core)', letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--text-subtle)', margin: '0 0 6px 2px' }}>
                      {d.day}
                    </div>
                    <Card padded={false} lift={2}>
                      {d.items.map((e, i) => (
                        <ExpenseRow key={e.id} title={e.title} category={e.category} paidBy={e.paidBy.split(' ')[0]} date={e.date}
                          yourShare={e.yourShare} settled={e.settled} divider={i < d.items.length - 1}
                          onClick={() => onOpenExpense(e)} />
                      ))}
                    </Card>
                  </div>
                ))}
              </div>}
        </div>
      </Pad>
    </Screen>
  );
}

/* ---------------- Expense detail sheet ---------------- */
function ExpenseDetailSheet({ expense, onClose, onDelete }) {
  const e = expense || {};
  const splits = e.splits || [];
  const sum = splits.reduce((a, s) => a + s.value, 0) || 1;
  return (
    <Sheet open={!!expense} onClose={onClose} height="82%" title={null}
      footer={
        <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
          <Button variant="outline" size="lg" icon="trash-2" onClick={onDelete} />
          <Button block size="lg" icon="check" onClick={onClose}>Done</Button>
        </div>
      }>
      {expense && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
            <CatDisc category={e.category} size={56} radius="var(--radius-lg)" />
            <div style={{ minWidth: 0 }}>
              <div style={{ font: '800 24px/1.1 var(--font-core)', letterSpacing: '-0.02em', color: 'var(--ink)' }}>{e.title}</div>
              <div style={{ fontSize: 'var(--text-caption)', color: 'var(--text-muted)', marginTop: 3 }}>
                {CAT_LABEL[e.category] || 'Other'} · {e.date}
              </div>
            </div>
          </div>

          <Card tone="sunk" lift={0} style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 12 }}>
            <div>
              <div style={{ font: '800 11px/1 var(--font-core)', letterSpacing: '0.05em', textTransform: 'uppercase', color: 'var(--text-subtle)' }}>Total</div>
              <div style={{ marginTop: 6 }}><Amount value={e.total} size="lg" /></div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ font: '800 11px/1 var(--font-core)', letterSpacing: '0.05em', textTransform: 'uppercase', color: 'var(--text-subtle)' }}>
                {e.settled ? 'Settled' : e.yourShare < 0 ? 'You owe' : 'You get'}
              </div>
              <div style={{ marginTop: 6 }}>
                <Amount value={e.yourShare} size="lg" tone={e.settled ? 'settled' : e.yourShare < 0 ? 'owe' : 'owed'} showSign={!e.settled} />
              </div>
            </div>
          </Card>

          <div>
            <SectionLabel>Paid by</SectionLabel>
            <Card padded={false} lift={2}>
              <ListRow divider={false}
                leading={<Avatar name={e.paidBy || 'You'} hue={(splits.find((s) => s.name === e.paidBy) || {}).hue || 6} size={40} />}
                title={e.paidBy}
                subtitle="fronted the whole bill"
                trailing={<Amount value={e.total} />} />
            </Card>
          </div>

          <div>
            <SectionLabel right={<Chip size="sm">Even split</Chip>}>How it was split</SectionLabel>
            <SplitBar people={splits} total={e.total} showLabels={false} height={44} />
            <div style={{ marginTop: 'var(--space-3)' }}>
              <Card padded={false} lift={2}>
                {splits.map((s, i) => (
                  <ListRow key={s.name} divider={i < splits.length - 1}
                    leading={<Avatar name={s.name} hue={s.hue} size={36} />}
                    title={s.name}
                    subtitle={`${Math.round((s.value / sum) * 100)}% of the bill`}
                    trailing={<Amount value={s.value} tone={s.name === e.paidBy ? 'owed' : 'neutral'} />} />
                ))}
              </Card>
            </div>
          </div>

          {e.note && (
            <div>
              <SectionLabel>Note</SectionLabel>
              <Card tone="lemon" lift={2}>
                <span style={{ fontSize: 'var(--text-body-lg)', color: 'var(--ink)' }}>{e.note}</span>
              </Card>
            </div>
          )}
        </div>
      )}
    </Sheet>
  );
}

/* ---------------- Activity ---------------- */
function Activity({ groups, onOpenExpense }) {
  const all = groups.flatMap((g) => g.expenses.map((e) => ({ ...e, group: g.name })));
  return (
    <Screen>
      <Pad>
        <div>
          <SectionLabel>Latest across all groups</SectionLabel>
          <Card padded={false} lift={2}>
            {all.map((e, i) => (
              <ExpenseRow key={e.id} title={e.title} category={e.category} paidBy={e.paidBy.split(' ')[0]}
                date={`${e.group} · ${e.date}`} yourShare={e.yourShare} settled={e.settled}
                divider={i < all.length - 1} onClick={() => onOpenExpense(e)} />
            ))}
          </Card>
        </div>
      </Pad>
    </Screen>
  );
}

/* ---------------- You / friends ---------------- */
function You({ members }) {
  return (
    <Screen>
      <Pad>
        <Card lift={4} style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-4)' }}>
          <Avatar name="You" hue={6} size={64} />
          <div>
            <div style={{ font: '800 19px/1.2 var(--font-core)', letterSpacing: '-0.012em' }}>You</div>
            <div style={{ fontSize: 'var(--text-caption)', color: 'var(--text-muted)', marginTop: 2 }}>you@tally.app</div>
          </div>
        </Card>
        <div>
          <SectionLabel>Friends</SectionLabel>
          <Card padded={false} lift={2}>
            {members.slice(1).map((m, i) => (
              <ListRow key={m.name} leading={<Avatar name={m.name} hue={m.hue} size={36} />} title={m.name}
                subtitle="2 shared groups" chevron divider={i < members.length - 2} />
            ))}
          </Card>
        </div>
        <div>
          <SectionLabel>Settings</SectionLabel>
          <Card padded={false} lift={2}>
            <ListRow title="Currency" subtitle="US Dollar" chevron />
            <ListRow title="Notifications" subtitle="Nudges on, digests off" chevron />
            <ListRow title="Sign out" divider={false} />
          </Card>
        </div>
      </Pad>
    </Screen>
  );
}

/* ---------------- Add expense sheet ---------------- */
function AddExpenseSheet({ open, onClose, group, onSave }) {
  const [step, setStep] = React.useState(0);
  const [digits, setDigits] = React.useState('0');
  const [cat, setCat] = React.useState('food');
  const [title, setTitle] = React.useState('');
  const [payer, setPayer] = React.useState('You');
  const [included, setIncluded] = React.useState(group.members.map((m) => m.name));
  const [mode, setMode] = React.useState('even');
  const [weights, setWeights] = React.useState(group.members.map((m) => ({ ...m, value: 25 })));

  React.useEffect(() => { if (open) { setStep(0); setDigits('0'); setTitle(''); setMode('even'); setPayer('You'); setIncluded(group.members.map((m) => m.name)); setWeights(group.members.map((m) => ({ ...m, value: 25 }))); } }, [open, group]);

  const total = Number(digits) || 0;
  const key = (k) => setDigits((v) => {
    if (k === 'del') return v.length > 1 ? v.slice(0, -1) : '0';
    if (k === '.') return v.includes('.') ? v : v + '.';
    if (v.includes('.') && v.split('.')[1].length >= 2) return v;
    return v === '0' ? k : v + k;
  });

  const active = weights.filter((w) => included.includes(w.name));
  const even = active.map((w) => ({ ...w, value: 100 / (active.length || 1) }));
  const shown = mode === 'even' ? even : active;
  const sum = shown.reduce((a, b) => a + b.value, 0) || 1;

  return (
    <Sheet open={open} onClose={onClose} height="88%" title={step === 0 ? 'How much?' : 'Who split it?'}
      footer={
        step === 0
          ? <Button block size="lg" disabled={total <= 0} onClick={() => setStep(1)} iconRight="arrow-right">Next</Button>
          : <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
              <Button variant="outline" size="lg" onClick={() => setStep(0)} icon="arrow-left" />
              <Button block size="lg" variant="solid" tone="mint" icon="check"
                onClick={() => onSave({ title: title || 'Expense', total, cat, payer, splits: shown.map((s) => ({ name: s.name, hue: s.hue, value: total * (s.value / sum) })) })}>Save split</Button>
            </div>
      }>
      {step === 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <AmountInput value={digits} />
          <CategoryPicker value={cat} onChange={setCat} />
          <Input label="What was it?" icon="receipt" value={title} placeholder="Dinner at Sichuan Rose" onChange={(ev) => setTitle(ev.target.value)} />
          <Keypad onKey={key} />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          <div>
            <SectionLabel>Who paid</SectionLabel>
            <div style={{ display: 'flex', gap: 'var(--gap-inline)', overflowX: 'auto', paddingBottom: 4 }}>
              {group.members.map((m) => (
                <Chip key={m.name} selected={payer === m.name} onClick={() => setPayer(m.name)}>{m.name.split(' ')[0]}</Chip>
              ))}
            </div>
          </div>
          <div>
            <SectionLabel>Split between</SectionLabel>
            <PersonToggleRow people={group.members} selected={included}
              onToggle={(n) => setIncluded((s) => (s.includes(n) ? s.filter((x) => x !== n) : [...s, n]))} />
          </div>
          <div>
            <SectionLabel right={
              <div style={{ display: 'flex', gap: 6 }}>
                <Chip size="sm" selected={mode === 'even'} onClick={() => { setMode('even'); setWeights((w) => w.map((x) => ({ ...x, value: 25 }))); }}>Evenly</Chip>
                <Chip size="sm" selected={mode === 'custom'} onClick={() => setMode('custom')}>Custom</Chip>
              </div>
            }>How</SectionLabel>
            <SplitBar people={shown} total={total}
              onChange={(next) => { setMode('custom'); setWeights((prev) => prev.map((p) => { const n = next.find((x) => x.name === p.name); return n ? { ...p, value: n.value } : p; })); }} />
          </div>
          <div>
            <SectionLabel>Shares</SectionLabel>
            <Card padded={false} lift={2}>
              {shown.map((p, i) => (
                <ListRow key={p.name} leading={<Avatar name={p.name} hue={p.hue} size={36} />} title={p.name.split(' ')[0]}
                  subtitle={p.name === payer ? 'paid the bill' : undefined}
                  divider={i < shown.length - 1}
                  trailing={<Amount value={total * (p.value / sum)} />} />
              ))}
            </Card>
          </div>
        </div>
      )}
    </Sheet>
  );
}

/* ---------------- Settle up sheet ---------------- */
function SettleUpSheet({ open, onClose, group, onDone }) {
  const owing = group.balances.filter((b) => b.amount !== 0);
  // History of names in the order they were marked, so a single tap can undo the last one.
  const [paid, setPaid] = React.useState([]);
  React.useEffect(() => { if (open) setPaid([]); }, [open]);

  const mark = (name) => setPaid((p) => (p.includes(name) ? p : [...p, name]));
  const revert = (name) => setPaid((p) => p.filter((x) => x !== name));
  const revertLast = () => setPaid((p) => p.slice(0, -1));
  const revertAll = () => setPaid([]);

  const last = paid[paid.length - 1];
  const done = paid.length >= owing.length && owing.length > 0;

  return (
    <Sheet open={open} onClose={onClose} height="76%" title="Settle up"
      footer={
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          {last && (
            <Button block variant="outline" size="md" icon="rotate-cw" onClick={revertLast}>
              {`Undo ${last.split(' ')[0]}`}
            </Button>
          )}
          <Button block size="lg" variant="solid" tone={done ? 'mint' : 'action'} icon={done ? 'party-popper' : 'handshake'} onClick={onDone}>
            {done ? "We're all square" : 'Done for now'}
          </Button>
        </div>
      }>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        <div>
          <SectionLabel right={paid.length > 1
            ? <Chip size="sm" icon="rotate-cw" onClick={revertAll}>Reset all</Chip>
            : null}>Marked as paid</SectionLabel>
          <ProgressBar value={paid.length} max={owing.length || 1} tone="mint" height={14} />
        </div>

        <Card padded={false} lift={2}>
          {owing.map((b, i) => {
            const isPaid = paid.includes(b.name);
            const divider = i < owing.length - 1;
            // A marked row keeps its place in the list and offers an immediate revert,
            // so tapping the wrong person is a one-tap mistake, never a lost balance.
            return isPaid ? (
              <ListRow key={b.name} divider={divider}
                leading={<Avatar name={b.name} hue={b.hue} size={40} dimmed />}
                title={<span style={{ color: 'var(--text-muted)', textDecoration: 'line-through', textDecorationThickness: '2px' }}>{b.name}</span>}
                subtitle="marked as paid"
                trailing={
                  <span style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
                    <Amount value={b.amount} tone="settled" size="md" />
                    <Button size="sm" variant="outline" icon="rotate-cw" onClick={() => revert(b.name)}>Undo</Button>
                  </span>
                } />
            ) : (
              <BalanceRow key={b.name} name={b.name} hue={b.hue} amount={b.amount} size="md"
                onSettle={() => mark(b.name)} divider={divider} />
            );
          })}
        </Card>

        <p style={{ margin: 0, fontSize: 'var(--text-body)', color: 'var(--text-muted)' }}>
          Tally doesn't move money. Mark what you've squared up in real life — tap <b>Undo</b> if you mark the wrong person.
        </p>
      </div>
    </Sheet>
  );
}

Object.assign(window, { Screen, Pad, SectionLabel, Stat, CatDisc, GroupsHome, OverallScreen, GroupDetail, ExpenseDetailSheet, Activity, You, AddExpenseSheet, SettleUpSheet });
