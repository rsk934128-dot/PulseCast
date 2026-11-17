import { useEffect, useRef, useState } from "react";
import { MessageSquare, Plus, Search, Send, Users, X } from "lucide-react";
import { createChatThread, observeMessages, sendMessage } from "../repository";
import type { ChatMessage, ChatThread, CreatorProfile } from "../types";

interface ChatsViewProps {
  uid: string;
  creatorName: string;
  chats: ChatThread[];
  creators: CreatorProfile[];
  onNotify: (msg: string) => void;
}

export function ChatsView({
  uid,
  creatorName,
  chats,
  creators,
  onNotify
}: ChatsViewProps) {
  const [search, setSearch] = useState("");
  const [selectedChatId, setSelectedChatId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [showNewModal, setShowNewModal] = useState(false);
  const endRef = useRef<HTMLDivElement | null>(null);

  const activeChat = chats.find((c) => c.id === selectedChatId) || chats[0] || null;

  useEffect(() => {
    if (!activeChat) {
      setMessages([]);
      return;
    }
    return observeMessages(activeChat.id, uid, setMessages);
  }, [activeChat?.id, uid]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  const filteredChats = chats.filter((chat) => {
    const q = search.trim().toLowerCase();
    if (!q) return true;
    return (
      chat.title.toLowerCase().includes(q) ||
      chat.lastMessageText.toLowerCase().includes(q)
    );
  });

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeChat || !draft.trim()) return;
    setSending(true);
    try {
      await sendMessage({
        chatId: activeChat.id,
        uid,
        senderName: creatorName,
        participantIds: activeChat.participantIds,
        text: draft
      });
      setDraft("");
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Failed to send message.");
    } finally {
      setSending(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Recent Messages</h1>
          <p className="page-subtitle">
            Real-time 1-on-1 and group conversations synced with PulseCast Android.
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setShowNewModal(true)}>
          <Plus size={18} />
          New Chat
        </button>
      </div>

      <div className="chats-layout">
        <section className="chat-list-panel">
          <div style={{ padding: 14, borderBottom: "1px solid var(--border-subtle)" }}>
            <div style={{ position: "relative" }}>
              <Search
                size={16}
                style={{
                  position: "absolute",
                  left: 12,
                  top: "50%",
                  transform: "translateY(-50%)",
                  color: "var(--text-muted)"
                }}
              />
              <input
                className="input"
                style={{ paddingLeft: 36 }}
                placeholder="Search conversations..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>

          <div className="chat-list-scroll">
            {filteredChats.length === 0 ? (
              <div style={{ padding: 24, textAlign: "center", color: "var(--text-secondary)" }}>
                <MessageSquare size={26} style={{ marginBottom: 8 }} />
                <div>No conversations yet. Start a chat with a creator.</div>
              </div>
            ) : (
              filteredChats.map((chat) => (
                <button
                  key={chat.id}
                  type="button"
                  className={`chat-row ${activeChat?.id === chat.id ? "active" : ""}`}
                  onClick={() => setSelectedChatId(chat.id)}
                >
                  <div className="avatar">
                    {chat.isGroup ? <Users size={18} /> : chat.title.charAt(0).toUpperCase()}
                  </div>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: "0.94rem", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                      {chat.title}
                    </div>
                    <div style={{ color: "var(--text-secondary)", fontSize: "0.8rem", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", marginTop: 2 }}>
                      {chat.lastMessageText}
                    </div>
                  </div>
                </button>
              ))
            )}
          </div>
        </section>

        <section className="chat-thread-panel">
          {!activeChat ? (
            <div className="empty-state" style={{ margin: "auto", border: "none" }}>
              <MessageSquare size={32} style={{ color: "var(--accent-cyan)", marginBottom: 8 }} />
              <h3>Select or start a conversation</h3>
              <p>Messages sync instantly across your phone and browser.</p>
            </div>
          ) : (
            <>
              <div
                style={{
                  padding: "16px 20px",
                  borderBottom: "1px solid var(--border-subtle)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between"
                }}
              >
                <div>
                  <div style={{ fontWeight: 700, fontSize: "1.05rem" }}>{activeChat.title}</div>
                  <div style={{ fontSize: "0.8rem", color: "var(--text-secondary)" }}>
                    {activeChat.participantIds.length} participants
                  </div>
                </div>
                <span className="badge">{activeChat.isGroup ? "Group" : "Direct"}</span>
              </div>

              <div className="messages-scroll">
                {messages.length === 0 ? (
                  <div style={{ textAlign: "center", color: "var(--text-muted)", margin: "auto" }}>
                    Say hello to start this thread!
                  </div>
                ) : (
                  messages.map((msg) => {
                    const mine = msg.senderId === uid;
                    return (
                      <div key={msg.id} className={`msg-bubble ${mine ? "mine" : ""}`}>
                        {!mine && <div className="msg-sender">{msg.senderName}</div>}
                        <div>{msg.text}</div>
                      </div>
                    );
                  })
                )}
                <div ref={endRef} />
              </div>

              <form
                onSubmit={handleSend}
                style={{
                  padding: 14,
                  borderTop: "1px solid var(--border-subtle)",
                  display: "flex",
                  gap: 10
                }}
              >
                <input
                  className="input"
                  placeholder="Write a message..."
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  maxLength={2000}
                />
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={sending || !draft.trim()}
                >
                  <Send size={16} />
                  Send
                </button>
              </form>
            </>
          )}
        </section>
      </div>

      {showNewModal && (
        <NewChatModal
          uid={uid}
          creatorName={creatorName}
          creators={creators.filter((c) => c.userId !== uid)}
          onClose={() => setShowNewModal(false)}
          onCreated={(id) => {
            setSelectedChatId(id);
            setShowNewModal(false);
          }}
          onNotify={onNotify}
        />
      )}
    </div>
  );
}

function NewChatModal({
  uid,
  creatorName,
  creators,
  onClose,
  onCreated,
  onNotify
}: {
  uid: string;
  creatorName: string;
  creators: CreatorProfile[];
  onClose: () => void;
  onCreated: (chatId: string) => void;
  onNotify: (msg: string) => void;
}) {
  const [title, setTitle] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);

  const toggleCreator = (userId: string) => {
    setSelectedIds((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId]
    );
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const picked = creators.filter((c) => selectedIds.includes(c.userId));
    const resolvedTitle =
      title.trim() ||
      (picked.length === 1 ? picked[0].displayName : picked.length > 1 ? "Studio Group" : "Creator Notes");

    setCreating(true);
    try {
      const id = await createChatThread({
        uid,
        creatorName,
        title: resolvedTitle,
        isGroup: picked.length > 1,
        participantIds: picked.map((p) => p.userId),
        participantNames: picked.map((p) => p.displayName)
      });
      onCreated(id);
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Failed to create conversation.");
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Start Conversation</h3>
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose}>
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleCreate} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div>
            <label className="field-label">Thread Title (optional)</label>
            <input
              className="input"
              placeholder="Podcast Episode #12"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div>
            <label className="field-label">Select Creators ({selectedIds.length} selected)</label>
            <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight: 220, overflowY: "auto" }}>
              {creators.length === 0 ? (
                <div style={{ fontSize: "0.86rem", color: "var(--text-secondary)" }}>
                  No other discoverable creators signed in yet. You can still start a personal Studio Notes thread.
                </div>
              ) : (
                creators.map((c) => {
                  const checked = selectedIds.includes(c.userId);
                  return (
                    <button
                      key={c.userId}
                      type="button"
                      className={`chat-row ${checked ? "active" : ""}`}
                      onClick={() => toggleCreator(c.userId)}
                    >
                      <div className="avatar">{c.displayName.charAt(0).toUpperCase()}</div>
                      <div style={{ textAlign: "left" }}>
                        <div style={{ fontWeight: 600 }}>{c.displayName}</div>
                        <div style={{ fontSize: "0.78rem", color: "var(--text-secondary)" }}>
                          @{c.handle}
                        </div>
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>

          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={creating}>
              {creating ? "Creating..." : "Create Thread"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
