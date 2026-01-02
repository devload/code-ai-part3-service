import * as vscode from 'vscode';
import axios from 'axios';

// 리뷰 결과 타입
interface ReviewComment {
    type: 'PRAISE' | 'SUGGESTION' | 'ISSUE' | 'CRITICAL' | 'ERROR';
    line: number;
    message: string;
    suggestion?: string;
}

interface CodeQualityScore {
    structureScore: number;
    readabilityScore: number;
    maintainabilityScore: number;
    reliabilityScore: number;
    securityScore: number;
    performanceScore: number;
    overallScore: number;
    grade: string;
}

interface ReviewResult {
    parseSuccess: boolean;
    score: CodeQualityScore;
    comments: ReviewComment[];
}

// 출력 채널
let outputChannel: vscode.OutputChannel;

// 진단 컬렉션 (에러/경고 표시용)
let diagnosticCollection: vscode.DiagnosticCollection;

export function activate(context: vscode.ExtensionContext) {
    console.log('Code AI Review 확장이 활성화되었습니다.');

    // 출력 채널 생성
    outputChannel = vscode.window.createOutputChannel('Code AI Review');

    // 진단 컬렉션 생성
    diagnosticCollection = vscode.languages.createDiagnosticCollection('codeai');
    context.subscriptions.push(diagnosticCollection);

    // AI Review 명령 등록
    let aiReviewCommand = vscode.commands.registerCommand('codeai.aiReview', async () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            vscode.window.showWarningMessage('파일을 열어주세요.');
            return;
        }

        if (editor.document.languageId !== 'java') {
            vscode.window.showWarningMessage('Java 파일만 리뷰할 수 있어요.');
            return;
        }

        await runAIReview(editor);
    });

    // Quick Score 명령 등록
    let quickScoreCommand = vscode.commands.registerCommand('codeai.quickScore', async () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor) {
            return;
        }

        if (editor.document.languageId !== 'java') {
            vscode.window.showWarningMessage('Java 파일만 분석할 수 있어요.');
            return;
        }

        await showQuickScore(editor);
    });

    context.subscriptions.push(aiReviewCommand, quickScoreCommand);
}

async function runAIReview(editor: vscode.TextEditor) {
    const document = editor.document;
    const code = document.getText();
    const fileName = document.fileName.split('/').pop() || 'unknown';

    // 진행 표시
    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Notification,
        title: 'AI 코드 리뷰 중...',
        cancellable: false
    }, async (progress) => {
        progress.report({ increment: 30, message: '코드 분석 중...' });

        try {
            const result = await callAIReviewAPI(code);
            progress.report({ increment: 70, message: '결과 처리 중...' });

            // 결과 표시
            displayReviewResult(document.uri, fileName, result);

            // 알림
            vscode.window.showInformationMessage(
                `리뷰 완료! 등급: ${result.score.grade}, 코멘트: ${result.comments.length}개`
            );
        } catch (error) {
            vscode.window.showErrorMessage(`리뷰 실패: ${error}`);
        }
    });
}

async function showQuickScore(editor: vscode.TextEditor) {
    const code = editor.document.getText();

    try {
        const result = await callAIReviewAPI(code);
        const score = result.score;

        // 상태 바 아이템으로 점수 표시
        const statusBarItem = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Right,
            100
        );

        const gradeEmoji = getGradeEmoji(score.grade);
        statusBarItem.text = `${gradeEmoji} ${score.grade} (${score.overallScore}/100)`;
        statusBarItem.tooltip = `구조: ${score.structureScore}\n가독성: ${score.readabilityScore}\n유지보수성: ${score.maintainabilityScore}\n신뢰성: ${score.reliabilityScore}\n보안: ${score.securityScore}\n성능: ${score.performanceScore}`;
        statusBarItem.show();

        // 5초 후 자동 숨김
        setTimeout(() => statusBarItem.dispose(), 5000);

    } catch (error) {
        vscode.window.showErrorMessage(`점수 계산 실패: ${error}`);
    }
}

async function callAIReviewAPI(code: string): Promise<ReviewResult> {
    const config = vscode.workspace.getConfiguration('codeai');
    const serverUrl = config.get<string>('serverUrl', 'http://localhost:8080');

    // 서버가 없으면 로컬 분석 (시뮬레이션)
    // 실제 프로덕션에서는 서버 API 호출
    return simulateLocalAnalysis(code);
}

// 로컬 분석 시뮬레이션 (서버 없이 동작)
function simulateLocalAnalysis(code: string): ReviewResult {
    const lines = code.split('\n');
    const comments: ReviewComment[] = [];

    // 간단한 규칙 기반 분석
    lines.forEach((line, index) => {
        const lineNum = index + 1;

        // System.out.println 검사
        if (line.includes('System.out.println')) {
            comments.push({
                type: 'SUGGESTION',
                line: lineNum,
                message: '프로덕션 코드에서 System.out.println 대신 로깅 프레임워크를 사용하는 게 좋아요.'
            });
        }

        // 빈 catch 블록 검사
        if (line.includes('catch') && lines[index + 1]?.trim() === '}') {
            comments.push({
                type: 'ISSUE',
                line: lineNum,
                message: 'catch 블록이 비어 있어요. 최소한 로그라도 남기는 게 좋아요.'
            });
        }

        // TODO 주석 검사
        if (line.includes('TODO')) {
            comments.push({
                type: 'SUGGESTION',
                line: lineNum,
                message: 'TODO 주석이 있네요. 기술 부채 관리가 필요해요.'
            });
        }
    });

    // 점수 계산 (간단한 휴리스틱)
    const baseScore = 100;
    const penalty = comments.length * 5;
    const overallScore = Math.max(0, baseScore - penalty);

    return {
        parseSuccess: true,
        score: {
            structureScore: Math.max(0, 90 - comments.length * 3),
            readabilityScore: Math.max(0, 85 - comments.length * 2),
            maintainabilityScore: Math.max(0, 80 - comments.length * 4),
            reliabilityScore: Math.max(0, 85 - comments.length * 3),
            securityScore: 90,
            performanceScore: 85,
            overallScore: overallScore,
            grade: getGrade(overallScore)
        },
        comments: comments
    };
}

function getGrade(score: number): string {
    if (score >= 90) return 'A';
    if (score >= 80) return 'B';
    if (score >= 70) return 'C';
    if (score >= 60) return 'D';
    return 'F';
}

function getGradeEmoji(grade: string): string {
    switch (grade) {
        case 'A': return '🌟';
        case 'B': return '👍';
        case 'C': return '👌';
        case 'D': return '⚠️';
        default: return '❌';
    }
}

function displayReviewResult(uri: vscode.Uri, fileName: string, result: ReviewResult) {
    // 출력 채널에 결과 표시
    outputChannel.clear();
    outputChannel.appendLine('='.repeat(60));
    outputChannel.appendLine(`🤖 AI 코드 리뷰 결과: ${fileName}`);
    outputChannel.appendLine('='.repeat(60));
    outputChannel.appendLine('');

    // 점수 표시
    const score = result.score;
    outputChannel.appendLine('📊 코드 품질 점수:');
    outputChannel.appendLine(`   구조:        ${score.structureScore}/100`);
    outputChannel.appendLine(`   가독성:      ${score.readabilityScore}/100`);
    outputChannel.appendLine(`   유지보수성:  ${score.maintainabilityScore}/100`);
    outputChannel.appendLine(`   신뢰성:      ${score.reliabilityScore}/100`);
    outputChannel.appendLine(`   보안:        ${score.securityScore}/100`);
    outputChannel.appendLine(`   성능:        ${score.performanceScore}/100`);
    outputChannel.appendLine(`   ──────────────────────`);
    outputChannel.appendLine(`   종합:        ${score.overallScore}/100  등급: ${score.grade}`);
    outputChannel.appendLine('');

    // 코멘트 표시
    outputChannel.appendLine(`📝 리뷰 코멘트: ${result.comments.length}개`);
    outputChannel.appendLine('-'.repeat(60));

    result.comments.forEach(comment => {
        const icon = getTypeIcon(comment.type);
        outputChannel.appendLine(`${icon} Line ${comment.line}: ${comment.message}`);
        if (comment.suggestion) {
            outputChannel.appendLine(`   💡 ${comment.suggestion}`);
        }
        outputChannel.appendLine('');
    });

    outputChannel.show();

    // 진단 정보 업데이트 (에디터에 문제 표시)
    updateDiagnostics(uri, result.comments);
}

function updateDiagnostics(uri: vscode.Uri, comments: ReviewComment[]) {
    const diagnostics: vscode.Diagnostic[] = comments.map(comment => {
        const range = new vscode.Range(
            new vscode.Position(comment.line - 1, 0),
            new vscode.Position(comment.line - 1, 1000)
        );

        const severity = getDiagnosticSeverity(comment.type);
        const diagnostic = new vscode.Diagnostic(range, comment.message, severity);
        diagnostic.source = 'Code AI';

        return diagnostic;
    });

    diagnosticCollection.set(uri, diagnostics);
}

function getDiagnosticSeverity(type: string): vscode.DiagnosticSeverity {
    switch (type) {
        case 'CRITICAL':
        case 'ERROR':
            return vscode.DiagnosticSeverity.Error;
        case 'ISSUE':
            return vscode.DiagnosticSeverity.Warning;
        case 'SUGGESTION':
            return vscode.DiagnosticSeverity.Information;
        default:
            return vscode.DiagnosticSeverity.Hint;
    }
}

function getTypeIcon(type: string): string {
    switch (type) {
        case 'PRAISE': return '👍';
        case 'SUGGESTION': return '💡';
        case 'ISSUE': return '⚠️';
        case 'CRITICAL': return '🚨';
        case 'ERROR': return '❌';
        default: return '📝';
    }
}

export function deactivate() {
    if (outputChannel) {
        outputChannel.dispose();
    }
}
