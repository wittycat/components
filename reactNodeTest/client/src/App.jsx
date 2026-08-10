import { useState, useEffect } from 'react';

const API = '/api/items';

export default function App() {
  const [items, setItems] = useState([]);
  const [form, setForm] = useState({ name: '', email: '' });
  const [editId, setEditId] = useState(null);
  const [editForm, setEditForm] = useState({ name: '', email: '' });

  // 加载数据
  const fetchItems = () => fetch(API).then(r => r.json()).then(setItems);

  useEffect(() => { fetchItems(); }, []);

  // 新增
  const handleCreate = async (e) => {
    e.preventDefault();
    if (!form.name || !form.email) return;
    await fetch(API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form),
    });
    setForm({ name: '', email: '' });
    fetchItems();
  };

  // 删除
  const handleDelete = async (id) => {
    await fetch(`${API}/${id}`, { method: 'DELETE' });
    fetchItems();
  };

  // 进入编辑
  const startEdit = (item) => {
    setEditId(item.id);
    setEditForm({ name: item.name, email: item.email });
  };

  // 取消编辑
  const cancelEdit = () => {
    setEditId(null);
    setEditForm({ name: '', email: '' });
  };

  // 保存编辑
  const handleUpdate = async (id) => {
    await fetch(`${API}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(editForm),
    });
    setEditId(null);
    fetchItems();
  };

  return (
    <div className="container">
      <h1>CRUD Demo</h1>
      <p className="subtitle">数据存储在 <code>data.properties</code></p>

      {/* 新增表单 */}
      <form className="add-form" onSubmit={handleCreate}>
        <input
          placeholder="姓名"
          value={form.name}
          onChange={e => setForm({ ...form, name: e.target.value })}
        />
        <input
          placeholder="邮箱"
          value={form.email}
          onChange={e => setForm({ ...form, email: e.target.value })}
        />
        <button type="submit">新增</button>
      </form>

      {/* 数据表格 */}
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>姓名</th>
            <th>邮箱</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {items.length === 0 && (
            <tr><td colSpan="4" className="empty">暂无数据</td></tr>
          )}
          {items.map(item => (
            <tr key={item.id}>
              <td>{item.id}</td>
              {editId === item.id ? (
                <>
                  <td>
                    <input
                      value={editForm.name}
                      onChange={e => setEditForm({ ...editForm, name: e.target.value })}
                    />
                  </td>
                  <td>
                    <input
                      value={editForm.email}
                      onChange={e => setEditForm({ ...editForm, email: e.target.value })}
                    />
                  </td>
                  <td className="actions">
                    <button className="btn-save" onClick={() => handleUpdate(item.id)}>保存</button>
                    <button className="btn-cancel" onClick={cancelEdit}>取消</button>
                  </td>
                </>
              ) : (
                <>
                  <td>{item.name}</td>
                  <td>{item.email}</td>
                  <td className="actions">
                    <button className="btn-edit" onClick={() => startEdit(item)}>编辑</button>
                    <button className="btn-delete" onClick={() => handleDelete(item.id)}>删除</button>
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
