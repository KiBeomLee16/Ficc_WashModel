import { useState } from "react";

const initialForm = {
  appId: "1",
  region: "NAMR",
  businessDate: "2026-06-08",
  requestedBy: "frontend-demo"
};

export default function App() {
  const [form, setForm] = useState(initialForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  function updateField(event) {
    setForm((current) => ({
      ...current,
      [event.target.name]: event.target.value
    }));
  }

  async function submitRunRequest(event) {
    event.preventDefault();
    setIsSubmitting(true);
    setResult(null);
    setError("");

    try {
      const response = await fetch("/run-request", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body: new URLSearchParams(form)
      });

      if (!response.ok) {
        throw new Error(`Request failed with HTTP ${response.status}`);
      }

      setResult(await response.json());
    } catch (submissionError) {
      setError(submissionError.message || "Run request submission failed.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="page-shell">
      <section className="workspace">
        <header className="top-bar">
          <div>
            <p className="eyebrow">FICC Wash Trade Surveillance</p>
            <h1>Run Request Console</h1>
          </div>
          <span className="environment-badge">Local MySQL</span>
        </header>

        <div className="content-grid">
          <section className="request-panel" aria-labelledby="request-title">
            <div className="section-heading">
              <h2 id="request-title">Create Run Request</h2>
              <p>Submit a queue row for the Java model worker.</p>
            </div>

            <form onSubmit={submitRunRequest} className="run-form">
              <label>
                <span>App ID</span>
                <input
                  name="appId"
                  inputMode="numeric"
                  value={form.appId}
                  onChange={updateField}
                  required
                />
              </label>

              <label>
                <span>Region</span>
                <select name="region" value={form.region} onChange={updateField}>
                  <option value="NAMR">NAMR</option>
                  <option value="EMEA">EMEA</option>
                  <option value="APAC">APAC</option>
                </select>
              </label>

              <label>
                <span>Business Date</span>
                <input
                  type="date"
                  name="businessDate"
                  value={form.businessDate}
                  onChange={updateField}
                  required
                />
              </label>

              <label>
                <span>Requested By</span>
                <input
                  name="requestedBy"
                  value={form.requestedBy}
                  onChange={updateField}
                  required
                />
              </label>

              <button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Submitting..." : "Submit Run Request"}
              </button>
            </form>

            {result && (
              <div className="status-message success" role="status">
                <strong>Request submitted</strong>
                <dl>
                  <div>
                    <dt>Request ID</dt>
                    <dd>{result.requestId}</dd>
                  </div>
                  <div>
                    <dt>Status</dt>
                    <dd>{result.status}</dd>
                  </div>
                  <div>
                    <dt>Region</dt>
                    <dd>{result.region}</dd>
                  </div>
                </dl>
              </div>
            )}

            {error && (
              <div className="status-message error" role="alert">
                <strong>Submission failed</strong>
                <p>{error}</p>
              </div>
            )}
          </section>

          <aside className="flow-panel" aria-label="surveillance flow">
            <div className="section-heading">
              <h2>Execution Flow</h2>
              <p>The UI only creates the request. Java owns the model run.</p>
            </div>

            <ol className="flow-list">
              <li>
                <span>1</span>
                <div>
                  <strong>Insert request</strong>
                  <p>Creates a `PENDING` row in `surveillance_run_request`.</p>
                </div>
              </li>
              <li>
                <span>2</span>
                <div>
                  <strong>Trigger worker</strong>
                  <p>`FiccRunRequestWorker` claims runnable queue rows.</p>
                </div>
              </li>
              <li>
                <span>3</span>
                <div>
                  <strong>Run model</strong>
                  <p>`getTrades()` and `evaluate()` execute the wash model.</p>
                </div>
              </li>
              <li>
                <span>4</span>
                <div>
                  <strong>Store result</strong>
                  <p>New alerts are saved with history and drill-out trades.</p>
                </div>
              </li>
            </ol>
          </aside>
        </div>
      </section>
    </main>
  );
}
