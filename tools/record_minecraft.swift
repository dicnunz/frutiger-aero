import Foundation
import AppKit
import ScreenCaptureKit
import AVFoundation
final class FrameAudit: NSObject, SCStreamOutput {
 var frames=[[String:Any]]()
 func stream(_ stream:SCStream,didOutputSampleBuffer sampleBuffer:CMSampleBuffer,of type:SCStreamOutputType){
  guard type == .screen, sampleBuffer.isValid else{return}
  let attachments=CMSampleBufferGetSampleAttachmentsArray(sampleBuffer,createIfNecessary:false) as? [[SCStreamFrameInfo:Any]]
  let status=attachments?.first?[.status] as? Int ?? -1
  frames.append(["pts":sampleBuffer.presentationTimeStamp.seconds,"received":ProcessInfo.processInfo.systemUptime,"status":status])
 }
}
final class RecordingDelegate: NSObject, SCRecordingOutputDelegate {
 private let lock=NSLock()
 private var finished=false
 private var finalized=false
 private var failure:Error?
 var marker=""
 var state:(finished:Bool,finalized:Bool,failure:Error?){lock.lock();defer{lock.unlock()};return(finished,finalized,failure)}
 private func fail(_ error:Error){lock.lock();failure=error;finished=true;lock.unlock();print("Recording failed: \(error)")}
 func recordingOutputDidStartRecording(_ output: SCRecordingOutput){
  do {try String(Date().timeIntervalSince1970).write(toFile:marker,atomically:true,encoding:.utf8);print("Minecraft window and app audio recording started")}
  catch {fail(error)}
 }
 func recordingOutput(_ output: SCRecordingOutput,didFailWithError error:Error){fail(error)}
 func recordingOutputDidFinishRecording(_ output:SCRecordingOutput){lock.lock();finalized=true;finished=true;lock.unlock();print("Recording finalized")}
}
@main struct Recorder {
 @MainActor static func main() async {
  do {try await record()}
  catch {fputs("Recording failed: \(error)\n",stderr);exit(1)}
 }
 @MainActor static func record() async throws {
  let app=NSApplication.shared;app.setActivationPolicy(.accessory)
  let args=CommandLine.arguments
  guard args.count==4,let pid=Int32(args[1]),let duration=Double(args[3]) else {fatalError("Usage: record_minecraft PID output.mp4 seconds")}
  let content=try await SCShareableContent.excludingDesktopWindows(true,onScreenWindowsOnly:false)
  guard let window=content.windows.first(where:{$0.owningApplication?.processID==pid && ($0.title?.contains("Minecraft") ?? false)}) else {fatalError("Target Minecraft window not found")}
  let filter=SCContentFilter(desktopIndependentWindow:window)
  let configuration=SCStreamConfiguration();configuration.width=1280;configuration.height=720;configuration.sourceRect=CGRect(x:0,y:28,width:window.frame.width,height:window.frame.height-28);configuration.minimumFrameInterval=CMTime(value:1,timescale:30);configuration.queueDepth=8;configuration.showsCursor=false;configuration.capturesAudio=true;configuration.excludesCurrentProcessAudio=true;configuration.captureMicrophone=false;configuration.sampleRate=48000;configuration.channelCount=2
  let stream=SCStream(filter:filter,configuration:configuration,delegate:nil)
  let audit=FrameAudit();let auditQueue=DispatchQueue(label:"aero.capture.audit",qos:.userInitiated)
  try stream.addStreamOutput(audit,type:.screen,sampleHandlerQueue:auditQueue)
  let output=SCRecordingOutputConfiguration();output.outputURL=URL(fileURLWithPath:args[2]);output.videoCodecType = .h264;output.outputFileType = .mp4
  let delegate=RecordingDelegate();delegate.marker=args[2]+".ready";let recording=SCRecordingOutput(configuration:output,delegate:delegate)
  try stream.addRecordingOutput(recording);try await stream.startCapture()
  for _ in 0..<Int(duration*4) {if delegate.state.finished || FileManager.default.fileExists(atPath:args[2]+".stop"){break};try await Task.sleep(for:.milliseconds(250))}
  var stopError:Error?
  do {try await stream.stopCapture()} catch {stopError=error}
  for _ in 0..<50 {if delegate.state.finished{break};try await Task.sleep(for:.milliseconds(100))}
  let state=delegate.state
  if let failure=state.failure{throw failure}
  guard state.finalized else {throw stopError ?? NSError(domain:"AeroRecorder",code:1,userInfo:[NSLocalizedDescriptionKey:"App audio recording did not finalize within 5 seconds"])}
  let auditData=try auditQueue.sync{try JSONSerialization.data(withJSONObject:["captureFps":30,"finalized":true,"frames":audit.frames],options:.prettyPrinted)}
  try auditData.write(to:URL(fileURLWithPath:args[2]+".capture.json"))
  print("Saved \(output.outputURL.path)")
 }
}
