import { useState } from "react";

const initialForm = {
  appId: "1",
  region: "NAMR",
  businessDate: "2026-06-08",
  requestedBy: "frontend-demo"
};

function normalizeSearchCriteria(values) {
  return {
    appId: String(values.appId).trim(),
    region: values.region.trim().toUpperCase(),
    businessDate: values.businessDate
  };
}

function alertReason(alert) {
  try {
    const payload = JSON.parse(alert.alertPayload);
    if (Array.isArray(payload.reasons) && payload.reasons.length > 0) {
      return payload.reasons.join(" ");
    }
  } catch {
    return "Stored alert payload could not be parsed.";
  }
  return "Stored alert payload is available in alert history.";
}

function relatedTrades(alert) {
  if (!alert.relatedTradeIds) {
    return "No related trades";
  }
  return alert.relatedTradeIds.split(",").join(" / ");
}

function formatDateTime(value) {
  if (!value) {
    return "-";
  }
  return String(value).replace("T", " ");
}

export default function App() {
  const [form, setForm] = useState(initialForm);
  const [searchedCriteria, setSearchedCriteria] = useState(normalizeSearchCriteria(initialForm));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [submitResult, setSubmitResult] = useState(null);
  const [searchResult, setSearchResult] = useState(null);
  const [submitError, setSubmitError] = useState("");
  const [searchError, setSearchError] = useState("");
  const canSearch = form.appId.trim() && form.region.trim() && form.businessDate;
  const alerts = searchResult?.alerts || [];
  const runRequests = searchResult?.runRequests || (searchResult?.runRequest ? [searchResult.runRequest] : []);
  const latestRunRequest = runRequests[0];
  const displayRunRequest = searchResult ? latestRunRequest : null;

  function updateField(event) {
    setForm((current) => ({
      ...current,
      [event.target.name]: event.target.value
    }));
  }

  async function searchAlertHistory() {
    const criteria = normalizeSearchCriteria(form);
    setSearchedCriteria(criteria);
    setSearchError("");
    setIsSearching(true);

    try {
      const params = new URLSearchParams(criteria);
      const response = await fetch(`/alert-history?${params.toString()}`);

      if (!response.ok) {
        throw new Error(`Search failed with HTTP ${response.status}`);
      }

      const data = await response.json();
      setSearchResult(data);
      if (data.runRequest) {
        setSubmitResult(data.runRequest);
      }
    } catch (searchFailure) {
      setSearchResult(null);
      setSearchError(searchFailure.message || "Alert history search failed.");
    } finally {
      setIsSearching(false);
    }
  }

  async function submitRunRequest(event) {
    event.preventDefault();
    const criteria = normalizeSearchCriteria(form);
    setIsSubmitting(true);
    setSubmitResult(null);
    setSearchResult(null);
    setSubmitError("");
    setSearchedCriteria(criteria);

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

      setSubmitResult(await response.json());
    } catch (submissionError) {
      setSubmitError(submissionError.message || "Run request submission failed.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="page-shell">
      <section className="workspace">
        <header className="top-bar">
          <div>
            <p className="eyebrow">Run Request Console</p>
            <h1>Trade Surveillance (6/4~6/8)</h1>
          </div>
          <span className="environment-badge">Local MySQL</span>
        </header>

        <div className="content-grid">
          <section className="request-panel" aria-labelledby="request-title">
            <div className="section-heading">
              <h2 id="request-title">Create Run Request</h2>
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
                  min="2026-06-04"
                  max="2026-06-08"
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

              <div className="form-actions">
                <button
                  className="secondary-action"
                  type="button"
                  disabled={!canSearch || isSearching}
                  onClick={searchAlertHistory}
                >
                  {isSearching ? "Searching..." : "Search Result"}
                </button>
                <button className="primary-action" type="submit" disabled={isSubmitting}>
                  {isSubmitting ? "Submitting..." : "Submit Run Request"}
                </button>
              </div>
            </form>

            {submitResult && (
              <div className="status-message success" role="status">
                <strong>Run request status</strong>
                <dl>
                  <div>
                    <dt>Request ID</dt>
                    <dd>{submitResult.requestId}</dd>
                  </div>
                  <div>
                    <dt>Status</dt>
                    <dd>{submitResult.status}</dd>
                  </div>
                  <div>
                    <dt>Region</dt>
                    <dd>{submitResult.region}</dd>
                  </div>
                  <div>
                    <dt>Alerts</dt>
                    <dd>{submitResult.alertsGenerated ?? "Pending"}</dd>
                  </div>
                </dl>
              </div>
            )}

            {submitError && (
              <div className="status-message error" role="alert">
                <strong>Submission failed</strong>
                <p>{submitError}</p>
              </div>
            )}
          </section>
        </div>

        <section className="result-panel" aria-labelledby="result-title">
          <div className="section-heading">
            <h2 id="result-title">Result Window</h2>
            <p>Search result is loaded from MySQL alert history for the selected app, region, and business date.</p>
          </div>

          <div className={alerts.length > 0 ? "result-window populated" : "result-window"}>
            <div className="result-grid">
              <div>
                <span>App ID</span>
                <strong>{searchedCriteria.appId}</strong>
              </div>
              <div>
                <span>Region</span>
                <strong>{searchedCriteria.region}</strong>
              </div>
              <div>
                <span>Business Date</span>
                <strong>{searchedCriteria.businessDate}</strong>
              </div>
              <div>
                <span>Alert History</span>
                <strong>{searchResult ? `${searchResult.alertCount} alert(s) found` : "Not searched"}</strong>
              </div>
              <div>
                <span>Request ID</span>
                <strong>{displayRunRequest?.requestId || (searchResult ? "No DB request" : "Not searched")}</strong>
              </div>
              <div>
                <span>Worker Status</span>
                <strong>{displayRunRequest?.status || (searchResult ? "No DB request" : "Waiting for search")}</strong>
              </div>
            </div>

            <p className="result-detail">
              {latestRunRequest
                ? `Latest request ${latestRunRequest.requestId} is ${latestRunRequest.status} with ${latestRunRequest.alertsGenerated} alert(s) generated.`
                : searchResult
                  ? `Loaded ${searchResult.alertCount} alert history row(s) for appId ${searchResult.appId}, region ${searchResult.region}, business date ${searchResult.businessDate}.`
                  : "Use Search Result to query stored alert history from MySQL."}
            </p>

            {searchError && (
              <div className="status-message error" role="alert">
                <strong>Search failed</strong>
                <p>{searchError}</p>
              </div>
            )}

            {runRequests.length > 0 ? (
              <div className="table-wrap">
                <table className="result-table request-table">
                  <thead>
                    <tr>
                      <th>Request ID</th>
                      <th>Status</th>
                      <th>Alerts Generated</th>
                      <th>Requested At</th>
                      <th>Started At</th>
                      <th>Completed At</th>
                    </tr>
                  </thead>
                  <tbody>
                    {runRequests.map((request) => (
                      <tr key={request.requestId}>
                        <td>{request.requestId}</td>
                        <td>{request.status}</td>
                        <td>{request.alertsGenerated}</td>
                        <td>{formatDateTime(request.requestedAt)}</td>
                        <td>{formatDateTime(request.startedAt)}</td>
                        <td>{formatDateTime(request.completedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}

            {alerts.length > 0 ? (
              <div className="table-wrap">
                <table className="result-table">
                  <thead>
                    <tr>
                      <th>Match Type</th>
                      <th>Alert ID</th>
                      <th>Related Trades</th>
                      <th>Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {alerts.map((alert) => (
                      <tr key={alert.alertHistoryId}>
                        <td>{alert.matchType}</td>
                        <td>{alert.alertId}</td>
                        <td>{relatedTrades(alert)}</td>
                        <td>{alertReason(alert)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}

            {displayRunRequest ? (
              <pre className="result-json">{JSON.stringify(displayRunRequest, null, 2)}</pre>
            ) : null}
          </div>
        </section>
      </section>
    </main>
  );
}
