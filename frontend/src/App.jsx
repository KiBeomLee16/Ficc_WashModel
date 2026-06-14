import { useState } from "react";

const initialForm = {
  appId: "1",
  region: "NAMR",
  businessDate: "2026-06-05",
  requestedBy: "frontend-demo"
};

const initialCalibrationForm = {
  appId: "4",
  region: "NAMRC",
  businessDate: "2026-06-05",
  requestedBy: "frontend-calibration",
  oneTimeMinTotalAmount: "100000000",
  cumulativeMinTotalAmount: "5000000",
  quantityTolerancePercent: "5",
  totalAmountTolerancePercent: "5",
  cumulativeLookupDays: "4"
};

const BUSINESS_DATE_MIN = "2026-06-01";
const BUSINESS_DATE_MAX = "2026-06-05";
const CALIBRATION_REGIONS = [
  { appId: "4", region: "NAMRC" },
  { appId: "5", region: "EMEAC" },
  { appId: "6", region: "APACC" }
];

const DEFAULT_TOLERANCE_THRESHOLD_PERCENT = 5;

function normalizeSearchCriteria(values) {
  return {
    appId: String(values.appId).trim(),
    region: values.region.trim().toUpperCase(),
    businessDate: values.businessDate
  };
}

function alertReason(alert) {
  const matchLabel = displayMatchType(alert.matchType).replace("_TRANSACTION", "").replace("_", "-").toLowerCase();
  const prefix = `${matchLabel.charAt(0).toUpperCase()}${matchLabel.slice(1)}`;

  try {
    const payload = JSON.parse(alert.alertPayload);
    if (Array.isArray(payload.reasons) && payload.reasons.length > 0) {
      const reasonText = payload.reasons.join(" ");

      if (/actual difference|matched amount/i.test(reasonText)) {
        return reasonText;
      }
    }

    const calculatedReason = calculateThresholdReason(prefix, payload);
    if (calculatedReason) {
      return calculatedReason;
    }

    if (Array.isArray(payload.reasons) && payload.reasons.length > 0) {
      const reasonText = payload.reasons.join(" ");

      const thresholdChecks = [];

      if (/quantity/i.test(reasonText)) {
        thresholdChecks.push("quantity tolerance");
      }
      if (/total amount/i.test(reasonText)) {
        thresholdChecks.push("total amount tolerance");
      }
      if (/minimum/i.test(reasonText)) {
        thresholdChecks.push("minimum amount");
      }

      if (thresholdChecks.length > 0) {
        return `${prefix} threshold breach: ${thresholdChecks.join(", ")}.`;
      }
    }
  } catch {
    return "Stored alert payload could not be parsed.";
  }
  return `${prefix} threshold breach: threshold conditions met.`;
}

function calculateThresholdReason(prefix, payload) {
  const totalBuyQuantity = toNumber(payload.totalBuyQuantity);
  const totalSellQuantity = toNumber(payload.totalSellQuantity);
  const totalBuyAmount = toNumber(payload.totalBuyAmount);
  const totalSellAmount = toNumber(payload.totalSellAmount);
  const thresholdAmount = toNumber(payload.thresholdAmount);

  if (
    totalBuyQuantity == null
    || totalSellQuantity == null
    || totalBuyAmount == null
    || totalSellAmount == null
    || thresholdAmount == null
  ) {
    return "";
  }

  const toleranceThreshold = toNumber(payload.quantityTolerancePercent)
    ?? toNumber(payload.totalAmountTolerancePercent)
    ?? DEFAULT_TOLERANCE_THRESHOLD_PERCENT;
  const matchedAmount = Math.min(totalBuyAmount, totalSellAmount);

  return [
    `${prefix} quantity tolerance: actual difference ${formatPercent(percentDifference(totalBuyQuantity, totalSellQuantity))}, threshold ${formatPercent(toleranceThreshold)}, within threshold.`,
    `${prefix} total amount tolerance: actual difference ${formatPercent(percentDifference(totalBuyAmount, totalSellAmount))}, threshold ${formatPercent(toleranceThreshold)}, within threshold.`,
    `${prefix} minimum amount: matched amount ${formatNumber(matchedAmount)}, threshold ${formatNumber(thresholdAmount)}, above threshold.`
  ].join(" ");
}

function toNumber(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : null;
}

function percentDifference(left, right) {
  const max = Math.max(left, right);
  if (max === 0) {
    return 0;
  }
  return (Math.abs(left - right) * 100) / max;
}

function formatPercent(value) {
  return `${formatNumber(value, 6)}%`;
}

function formatNumber(value, maximumFractionDigits = 6) {
  return new Intl.NumberFormat("en-US", {
    maximumFractionDigits,
    useGrouping: false
  }).format(value);
}

function displayMatchType(matchType) {
  if (matchType === "CUMULATIVE_TRANSACTION") {
    return "AGGREGATE_TRANSACTION";
  }
  return matchType || "-";
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

function resultMessageForRequest(request) {
  if (request.status !== "COMPLETED") {
    return `Request ${request.requestId} is ${request.status}. Result is not available yet.`;
  }
  if (request.alertsGenerated === 0) {
    return `Request ${request.requestId} completed with no generated alerts.`;
  }
  return `Request ${request.requestId} completed with ${request.alertsGenerated} generated alert(s).`;
}

export default function App() {
  const [form, setForm] = useState(initialForm);
  const [calibrationForm, setCalibrationForm] = useState(initialCalibrationForm);
  const [searchedCriteria, setSearchedCriteria] = useState(normalizeSearchCriteria(initialForm));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmittingCalibration, setIsSubmittingCalibration] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [submitResult, setSubmitResult] = useState(null);
  const [calibrationResult, setCalibrationResult] = useState(null);
  const [searchResult, setSearchResult] = useState(null);
  const [submitError, setSubmitError] = useState("");
  const [calibrationError, setCalibrationError] = useState("");
  const [searchError, setSearchError] = useState("");
  const canSearch = form.appId.trim() && form.region.trim() && form.businessDate;
  const canSearchCalibration = calibrationForm.appId.trim()
    && calibrationForm.region.trim()
    && calibrationForm.businessDate;
  const alerts = searchResult?.alerts || [];
  const runRequests = searchResult?.runRequests || (searchResult?.runRequest ? [searchResult.runRequest] : []);
  const displayedRunRequests = searchResult?.runRequest ? [searchResult.runRequest] : [];

  function updateField(event) {
    setForm((current) => ({
      ...current,
      [event.target.name]: event.target.value
    }));
  }

  function updateCalibrationField(event) {
    if (event.target.name === "region") {
      const selectedRegion = CALIBRATION_REGIONS.find((option) => option.region === event.target.value);
      setCalibrationForm((current) => ({
        ...current,
        appId: selectedRegion?.appId || current.appId,
        region: event.target.value
      }));
      return;
    }

    setCalibrationForm((current) => ({
      ...current,
      [event.target.name]: event.target.value
    }));
  }

  async function searchAlertHistory(criteriaSource = form) {
    const criteria = normalizeSearchCriteria(criteriaSource);
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
    setCalibrationResult(null);
    setCalibrationError("");
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

  async function submitCalibrationRunRequest(event) {
    event.preventDefault();
    const criteria = normalizeSearchCriteria(calibrationForm);
    setIsSubmittingCalibration(true);
    setCalibrationResult(null);
    setSearchResult(null);
    setSubmitResult(null);
    setCalibrationError("");
    setSubmitError("");
    setSearchedCriteria(criteria);

    try {
      const response = await fetch("/calibration-run-request", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body: new URLSearchParams(calibrationForm)
      });

      if (!response.ok) {
        throw new Error(`Calibration request failed with HTTP ${response.status}`);
      }

      setCalibrationResult(await response.json());
    } catch (submissionError) {
      setCalibrationError(submissionError.message || "Calibration request submission failed.");
    } finally {
      setIsSubmittingCalibration(false);
    }
  }

  return (
    <main className="page-shell">
      <section className="workspace">
        <header className="top-bar">
          <div>
            <p className="eyebrow">Run Request Console</p>
            <h1>Trade Surveillance (6/1~6/5)</h1>
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
                  min={BUSINESS_DATE_MIN}
                  max={BUSINESS_DATE_MAX}
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
                  onClick={() => searchAlertHistory(form)}
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

          <section className="request-panel calibration-panel" aria-labelledby="calibration-title">
            <div className="section-heading">
              <h2 id="calibration-title">Calibration Request</h2>
            </div>

            <form onSubmit={submitCalibrationRunRequest} className="run-form">
              <label>
                <span>Region</span>
                <select name="region" value={calibrationForm.region} onChange={updateCalibrationField}>
                  {CALIBRATION_REGIONS.map((option) => (
                    <option key={option.region} value={option.region}>{option.region}</option>
                  ))}
                </select>
              </label>

              <label>
                <span>App ID</span>
                <input
                  name="appId"
                  inputMode="numeric"
                  value={calibrationForm.appId}
                  readOnly
                  required
                />
              </label>

              <label>
                <span>Business Date</span>
                <input
                  type="date"
                  name="businessDate"
                  value={calibrationForm.businessDate}
                  min={BUSINESS_DATE_MIN}
                  max={BUSINESS_DATE_MAX}
                  onChange={updateCalibrationField}
                  required
                />
              </label>

              <label>
                <span>Requested By</span>
                <input
                  name="requestedBy"
                  value={calibrationForm.requestedBy}
                  onChange={updateCalibrationField}
                  required
                />
              </label>

              <fieldset className="threshold-fieldset">
                <legend>Custom Thresholds</legend>
                <label>
                  <span>One-Time Minimum Amount</span>
                  <input
                    name="oneTimeMinTotalAmount"
                    inputMode="decimal"
                    value={calibrationForm.oneTimeMinTotalAmount}
                    onChange={updateCalibrationField}
                    required
                  />
                </label>
                <label>
                  <span>Aggregate Minimum Amount</span>
                  <input
                    name="cumulativeMinTotalAmount"
                    inputMode="decimal"
                    value={calibrationForm.cumulativeMinTotalAmount}
                    onChange={updateCalibrationField}
                    required
                  />
                </label>
                <label>
                  <span>Quantity Tolerance %</span>
                  <input
                    name="quantityTolerancePercent"
                    inputMode="decimal"
                    value={calibrationForm.quantityTolerancePercent}
                    onChange={updateCalibrationField}
                    required
                  />
                </label>
                <label>
                  <span>Total Amount Tolerance %</span>
                  <input
                    name="totalAmountTolerancePercent"
                    inputMode="decimal"
                    value={calibrationForm.totalAmountTolerancePercent}
                    onChange={updateCalibrationField}
                    required
                  />
                </label>
                <label>
                  <span>Aggregate Lookup Days</span>
                  <input
                    name="cumulativeLookupDays"
                    inputMode="numeric"
                    value={calibrationForm.cumulativeLookupDays}
                    onChange={updateCalibrationField}
                    required
                  />
                </label>
              </fieldset>

              <div className="form-actions">
                <button
                  className="secondary-action"
                  type="button"
                  disabled={!canSearchCalibration || isSearching}
                  onClick={() => searchAlertHistory(calibrationForm)}
                >
                  {isSearching ? "Searching..." : "Search Result"}
                </button>
                <button className="primary-action" type="submit" disabled={isSubmittingCalibration}>
                  {isSubmittingCalibration ? "Submitting..." : "Submit Calibration Request"}
                </button>
              </div>
            </form>

            {calibrationResult && (
              <div className="status-message success" role="status">
                <strong>Calibration request status</strong>
                <dl>
                  <div>
                    <dt>Request ID</dt>
                    <dd>{calibrationResult.requestId}</dd>
                  </div>
                  <div>
                    <dt>Status</dt>
                    <dd>{calibrationResult.status}</dd>
                  </div>
                  <div>
                    <dt>Region</dt>
                    <dd>{calibrationResult.region}</dd>
                  </div>
                  <div>
                    <dt>One-Time Min</dt>
                    <dd>{calibrationResult.oneTimeMinTotalAmount}</dd>
                  </div>
                  <div>
                    <dt>Aggregate Min</dt>
                    <dd>{calibrationResult.cumulativeMinTotalAmount}</dd>
                  </div>
                  <div>
                    <dt>Lookup Days</dt>
                    <dd>{calibrationResult.cumulativeLookupDays}</dd>
                  </div>
                </dl>
              </div>
            )}

            {calibrationError && (
              <div className="status-message error" role="alert">
                <strong>Calibration failed</strong>
                <p>{calibrationError}</p>
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
                <span>Run Requests</span>
                <strong>{searchResult ? `${runRequests.length} request(s) found` : "Not searched"}</strong>
              </div>
              <div>
                <span>Result Source</span>
                <strong>{searchResult ? "MySQL" : "Waiting for search"}</strong>
              </div>
            </div>

            <p className="result-detail">
              {searchResult
                ? `Showing latest request result. Loaded ${runRequests.length} run request row(s) and ${searchResult.alertCount} alert history row(s) for appId ${searchResult.appId}, region ${searchResult.region}, business date ${searchResult.businessDate}.`
                : "Use Search Result to query stored alert history from MySQL."}
            </p>

            {searchError && (
              <div className="status-message error" role="alert">
                <strong>Search failed</strong>
                <p>{searchError}</p>
              </div>
            )}

            {displayedRunRequests.length > 0 ? (
              <div className="request-result-list">
                {displayedRunRequests.map((request) => {
                  const requestAlerts = alerts.filter((alert) => Number(alert.requestId) === Number(request.requestId));
                  const shouldShowAlerts = request.status === "COMPLETED"
                    && request.alertsGenerated > 0
                    && requestAlerts.length > 0;

                  return (
                    <section className="request-result-card" key={request.requestId}>
                      <div className="request-result-header">
                        <div>
                          <h3>Request ID {request.requestId}</h3>
                          <p>{resultMessageForRequest(request)}</p>
                        </div>
                        <span className={`status-pill ${request.status.toLowerCase()}`}>
                          {request.status}
                        </span>
                      </div>

                      <div className="request-meta-grid">
                        <div>
                          <span>Alerts Generated</span>
                          <strong>{request.alertsGenerated}</strong>
                        </div>
                        <div>
                          <span>Requested At</span>
                          <strong>{formatDateTime(request.requestedAt)}</strong>
                        </div>
                        <div>
                          <span>Started At</span>
                          <strong>{formatDateTime(request.startedAt)}</strong>
                        </div>
                        <div>
                          <span>Completed At</span>
                          <strong>{formatDateTime(request.completedAt)}</strong>
                        </div>
                      </div>

                      {shouldShowAlerts ? (
                        <div className="table-wrap">
                          <table className="result-table">
                            <thead>
                              <tr>
                                <th>Request ID</th>
                                <th>Match Type</th>
                                <th>Alert ID</th>
                                <th>Related Trades</th>
                                <th>Reason</th>
                              </tr>
                            </thead>
                            <tbody>
                              {requestAlerts.map((alert) => (
                                <tr key={`${request.requestId}-${alert.alertHistoryId}`}>
                                  <td>{alert.requestId}</td>
                                  <td>{displayMatchType(alert.matchType)}</td>
                                  <td>{alert.alertId}</td>
                                  <td>{relatedTrades(alert)}</td>
                                  <td>{alertReason(alert)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      ) : null}
                    </section>
                  );
                })}
              </div>
            ) : null}
          </div>
        </section>
      </section>
    </main>
  );
}
