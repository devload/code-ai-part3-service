/**
 * Code AI Dashboard JavaScript
 */

// API 클라이언트
const API = {
    baseUrl: '/api/v1',

    async analyze(code, options = {}) {
        const response = await fetch(`${this.baseUrl}/analyze`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                code,
                filename: options.filename || 'Code.java',
                options: {
                    includeAST: options.includeAST ?? true,
                    includeLLM: options.includeLLM ?? false,
                    llmProvider: options.provider || 'claude',
                    autoFix: options.autoFix ?? false
                }
            })
        });
        return response.json();
    },

    async quickScore(code) {
        const response = await fetch(`${this.baseUrl}/score`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code })
        });
        return response.json();
    },

    async getAnalyses(limit = 10) {
        const response = await fetch(`${this.baseUrl}/analyses?limit=${limit}`);
        return response.json();
    },

    async uploadFile(file, options = {}) {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('includeAST', options.includeAST ?? true);
        formData.append('includeLLM', options.includeLLM ?? false);
        formData.append('provider', options.provider || 'claude');

        const response = await fetch(`${this.baseUrl}/analyze/file`, {
            method: 'POST',
            body: formData
        });
        return response.json();
    }
};

// WebSocket 클라이언트
class WebSocketClient {
    constructor() {
        this.stompClient = null;
        this.connected = false;
        this.handlers = {};
    }

    connect() {
        const socket = new SockJS('/ws');
        this.stompClient = Stomp.over(socket);
        this.stompClient.debug = null; // 디버그 로그 비활성화

        this.stompClient.connect({}, (frame) => {
            this.connected = true;
            console.log('WebSocket 연결됨');

            // 구독 설정
            this.stompClient.subscribe('/topic/analysis', (message) => {
                this.emit('analysis', JSON.parse(message.body));
            });

            this.stompClient.subscribe('/topic/score', (message) => {
                this.emit('score', JSON.parse(message.body));
            });

            this.stompClient.subscribe('/topic/status', (message) => {
                this.emit('status', JSON.parse(message.body));
            });

            this.stompClient.subscribe('/topic/llm-result', (message) => {
                this.emit('llm-result', JSON.parse(message.body));
            });

            this.emit('connected');
        }, (error) => {
            this.connected = false;
            console.error('WebSocket 연결 실패:', error);
            this.emit('error', error);
        });
    }

    disconnect() {
        if (this.stompClient) {
            this.stompClient.disconnect();
            this.connected = false;
        }
    }

    analyze(code, options = {}) {
        if (!this.connected) return;
        this.stompClient.send('/app/analyze', {}, JSON.stringify({
            code,
            options: {
                includeAST: options.includeAST ?? true,
                includeLLM: options.includeLLM ?? false,
                llmProvider: options.provider || 'claude',
                autoFix: options.autoFix ?? false
            }
        }));
    }

    analyzeLLM(code, provider = 'claude') {
        if (!this.connected) return;
        this.stompClient.send('/app/analyze-llm', {}, JSON.stringify({
            code,
            options: { includeLLM: true, llmProvider: provider }
        }));
    }

    quickScore(code) {
        if (!this.connected) return;
        this.stompClient.send('/app/score', {}, JSON.stringify({ code }));
    }

    on(event, handler) {
        if (!this.handlers[event]) {
            this.handlers[event] = [];
        }
        this.handlers[event].push(handler);
    }

    emit(event, data) {
        if (this.handlers[event]) {
            this.handlers[event].forEach(handler => handler(data));
        }
    }
}

// UI 컴포넌트
const UI = {
    // 점수 표시 업데이트
    updateScore(score) {
        const circle = document.querySelector('.score-circle');
        const valueEl = document.querySelector('.score-value');
        const gradeEl = document.querySelector('.score-grade');

        if (circle && valueEl && gradeEl) {
            // 등급 클래스 초기화
            circle.className = 'score-circle';
            circle.classList.add(`grade-${score.grade.toLowerCase()}`);
            valueEl.textContent = score.overall;
            gradeEl.textContent = score.grade;

            // 카테고리 점수 업데이트
            this.updateCategories(score.categories);
        }
    },

    // 카테고리 점수 업데이트
    updateCategories(categories) {
        const container = document.querySelector('.category-scores');
        if (!container) return;

        container.innerHTML = '';
        for (const [name, value] of Object.entries(categories)) {
            const progressClass = value >= 80 ? 'success' : value >= 60 ? 'warning' : 'danger';
            container.innerHTML += `
                <div class="category-item">
                    <span class="category-name">${name}</span>
                    <div class="category-bar">
                        <div class="progress-bar">
                            <div class="progress-fill ${progressClass}" style="width: ${value}%"></div>
                        </div>
                    </div>
                    <span class="category-value">${value}</span>
                </div>
            `;
        }
    },

    // 이슈 목록 업데이트
    updateIssues(issues) {
        const container = document.querySelector('.issue-list');
        if (!container) return;

        if (issues.length === 0) {
            container.innerHTML = '<div class="loading">이슈가 발견되지 않았습니다.</div>';
            return;
        }

        container.innerHTML = issues.map(issue => `
            <li class="issue-item">
                <div class="issue-severity ${issue.severity.toLowerCase()}"></div>
                <div class="issue-content">
                    <div class="issue-title">${this.escapeHtml(issue.message)}</div>
                    <div class="issue-location">
                        ${issue.code} - Line ${issue.line}
                    </div>
                    ${issue.suggestion ? `
                        <div class="issue-suggestion">
                            💡 ${this.escapeHtml(issue.suggestion)}
                        </div>
                    ` : ''}
                </div>
            </li>
        `).join('');
    },

    // 긍정적 피드백 업데이트
    updatePositives(positives) {
        const container = document.querySelector('.positives-list');
        if (!container) return;

        if (positives.length === 0) {
            container.innerHTML = '<div class="loading">긍정적 피드백이 없습니다.</div>';
            return;
        }

        container.innerHTML = positives.map(item => `
            <li class="issue-item">
                <div class="issue-severity" style="background: var(--success)"></div>
                <div class="issue-content">
                    <div class="issue-title">${this.escapeHtml(item)}</div>
                </div>
            </li>
        `).join('');
    },

    // 통계 업데이트
    updateStatistics(stats) {
        const elements = {
            'stat-lines': stats.totalLines,
            'stat-code': stats.codeLines,
            'stat-methods': stats.methods,
            'stat-complexity': stats.complexity
        };

        for (const [id, value] of Object.entries(elements)) {
            const el = document.getElementById(id);
            if (el) el.textContent = value;
        }
    },

    // 로딩 표시
    showLoading(message = '분석 중...') {
        const container = document.querySelector('.analysis-result');
        if (container) {
            container.innerHTML = `
                <div class="loading">
                    <div class="spinner"></div>
                    <span>${message}</span>
                </div>
            `;
        }
    },

    // 상태 메시지
    showStatus(status, message) {
        const statusEl = document.getElementById('status-message');
        if (statusEl) {
            statusEl.textContent = message;
            statusEl.className = `badge badge-${status === 'COMPLETE' ? 'success' : status === 'ERROR' ? 'danger' : 'info'}`;
        }
    },

    // HTML 이스케이프
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};

// 메인 앱
const App = {
    ws: null,
    debounceTimer: null,

    init() {
        // WebSocket 연결
        this.ws = new WebSocketClient();
        this.ws.connect();

        // 이벤트 핸들러 설정
        this.setupEventHandlers();

        // 초기 데이터 로드
        this.loadRecentAnalyses();
    },

    setupEventHandlers() {
        // WebSocket 이벤트
        this.ws.on('analysis', (result) => {
            this.handleAnalysisResult(result);
        });

        this.ws.on('score', (score) => {
            UI.updateScore(score);
        });

        this.ws.on('status', (data) => {
            UI.showStatus(data.status, data.message);
        });

        this.ws.on('llm-result', (result) => {
            this.handleAnalysisResult(result);
            UI.showStatus('COMPLETE', 'LLM 분석 완료');
        });

        // 코드 에디터 이벤트
        const codeEditor = document.getElementById('code-editor');
        if (codeEditor) {
            codeEditor.addEventListener('input', () => {
                this.debounceScore(codeEditor.value);
            });
        }

        // 분석 버튼
        const analyzeBtn = document.getElementById('analyze-btn');
        if (analyzeBtn) {
            analyzeBtn.addEventListener('click', () => {
                this.analyzeCode();
            });
        }

        // LLM 분석 버튼
        const llmBtn = document.getElementById('llm-analyze-btn');
        if (llmBtn) {
            llmBtn.addEventListener('click', () => {
                this.analyzeLLM();
            });
        }

        // 파일 업로드
        const fileInput = document.getElementById('file-input');
        if (fileInput) {
            fileInput.addEventListener('change', (e) => {
                this.handleFileUpload(e.target.files[0]);
            });
        }

        // 드래그 앤 드롭
        const dropZone = document.getElementById('drop-zone');
        if (dropZone) {
            dropZone.addEventListener('dragover', (e) => {
                e.preventDefault();
                dropZone.classList.add('drag-over');
            });

            dropZone.addEventListener('dragleave', () => {
                dropZone.classList.remove('drag-over');
            });

            dropZone.addEventListener('drop', (e) => {
                e.preventDefault();
                dropZone.classList.remove('drag-over');
                const file = e.dataTransfer.files[0];
                if (file) this.handleFileUpload(file);
            });
        }
    },

    debounceScore(code) {
        clearTimeout(this.debounceTimer);
        this.debounceTimer = setTimeout(() => {
            if (code.length > 10) {
                this.ws.quickScore(code);
            }
        }, 500);
    },

    async analyzeCode() {
        const codeEditor = document.getElementById('code-editor');
        if (!codeEditor || !codeEditor.value.trim()) {
            alert('분석할 코드를 입력하세요.');
            return;
        }

        UI.showLoading('분석 중...');

        try {
            const result = await API.analyze(codeEditor.value, {
                includeAST: true,
                includeLLM: document.getElementById('include-llm')?.checked || false
            });
            this.handleAnalysisResult(result);
        } catch (error) {
            UI.showStatus('ERROR', '분석 실패: ' + error.message);
        }
    },

    analyzeLLM() {
        const codeEditor = document.getElementById('code-editor');
        if (!codeEditor || !codeEditor.value.trim()) {
            alert('분석할 코드를 입력하세요.');
            return;
        }

        UI.showLoading('LLM 분석 중...');
        const provider = document.getElementById('llm-provider')?.value || 'claude';
        this.ws.analyzeLLM(codeEditor.value, provider);
    },

    async handleFileUpload(file) {
        if (!file) return;

        // 파일 읽기
        const reader = new FileReader();
        reader.onload = (e) => {
            const codeEditor = document.getElementById('code-editor');
            if (codeEditor) {
                codeEditor.value = e.target.result;
            }
        };
        reader.readAsText(file);

        // 분석 실행
        UI.showLoading('파일 분석 중...');
        try {
            const result = await API.uploadFile(file);
            this.handleAnalysisResult(result);
        } catch (error) {
            UI.showStatus('ERROR', '파일 분석 실패: ' + error.message);
        }
    },

    handleAnalysisResult(result) {
        if (!result) return;

        if (result.scores) {
            UI.updateScore(result.scores);
        }
        if (result.issues) {
            UI.updateIssues(result.issues);
        }
        if (result.positives) {
            UI.updatePositives(result.positives);
        }
        if (result.statistics) {
            UI.updateStatistics(result.statistics);
        }

        // 고정된 코드 표시
        if (result.fixedCode) {
            const fixedCodeEl = document.getElementById('fixed-code');
            if (fixedCodeEl) {
                fixedCodeEl.value = result.fixedCode;
            }
        }

        UI.showStatus('COMPLETE', '분석 완료');
    },

    async loadRecentAnalyses() {
        try {
            const analyses = await API.getAnalyses(10);
            this.updateHistoryTable(analyses);
        } catch (error) {
            console.error('최근 분석 로드 실패:', error);
        }
    },

    updateHistoryTable(analyses) {
        const tbody = document.querySelector('.history-table tbody');
        if (!tbody) return;

        if (analyses.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" style="text-align:center">분석 기록이 없습니다.</td></tr>';
            return;
        }

        tbody.innerHTML = analyses.map(item => `
            <tr>
                <td>${item.filename || '-'}</td>
                <td><span class="badge badge-${this.getGradeBadge(item.scores?.grade)}">${item.scores?.grade || '-'}</span></td>
                <td>${item.scores?.overall || '-'}</td>
                <td>${item.issues?.length || 0}</td>
                <td>${new Date(item.timestamp).toLocaleString()}</td>
            </tr>
        `).join('');
    },

    getGradeBadge(grade) {
        switch (grade) {
            case 'A': return 'success';
            case 'B': return 'info';
            case 'C': return 'warning';
            default: return 'danger';
        }
    }
};

// DOM 로드 완료 시 앱 초기화
document.addEventListener('DOMContentLoaded', () => {
    App.init();
});
