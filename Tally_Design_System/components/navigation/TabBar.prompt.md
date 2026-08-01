Primary app navigation, 2 or 4 tabs plus a raised centre add button.

```jsx
<TabBar tabs={[{id:'groups',label:'Groups',icon:'users'},{id:'activity',label:'Activity',icon:'clock'},
               {id:'friends',label:'Friends',icon:'user-round'},{id:'you',label:'You',icon:'circle-user'}]}
        value={tab} onChange={setTab}
        centerAction={{ icon: 'plus', label: 'Add expense', onClick: add }} />
```

With `centerAction` set, use an even number of tabs so the slab sits dead centre.
