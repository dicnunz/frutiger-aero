import Foundation
import AppKit
import ScreenCaptureKit
import AVFoundation
final class RecordingDelegate: NSObject, SCRecordingOutputDelegate {
 var finished=false
 var marker=""
 func recordingOutputDidStartRecording(_ output: SCRecordingOutput){try? String(Date().timeIntervalSince1970).write(toFile:marker,atomically:true,encoding:.utf8);print("Minecraft window and app audio recording started")}
 func recordingOutput(_ output: SCRecordingOutput,didFailWithError error:Error){print("Recording failed: \(error)");finished=true}
 func recordingOutputDidFinishRecording(_ output:SCRecordingOutput){print("Recording finalized");finished=true}
}
@main struct Recorder {
 @MainActor static func main() async throws {
  let app=NSApplication.shared;app.setActivationPolicy(.accessory)
  let args=CommandLine.arguments
  guard args.count==4,let pid=Int32(args[1]),let duration=Double(args[3]) else {fatalError("Usage: record_minecraft PID output.mp4 seconds")}
  let content=try await SCShareableContent.excludingDesktopWindows(true,onScreenWindowsOnly:false)
  guard let window=content.windows.first(where:{$0.owningApplication?.processID==pid && ($0.title?.contains("Minecraft") ?? false)}) else {fatalError("Target Minecraft window not found")}
  let filter=SCContentFilter(desktopIndependentWindow:window)
  let configuration=SCStreamConfiguration();configuration.width=1280;configuration.height=720;configuration.sourceRect=CGRect(x:0,y:28,width:window.frame.width,height:window.frame.height-28);configuration.minimumFrameInterval=CMTime(value:1,timescale:60);configuration.showsCursor=false;configuration.capturesAudio=true;configuration.excludesCurrentProcessAudio=true;configuration.captureMicrophone=false;configuration.sampleRate=48000;configuration.channelCount=2
  let stream=SCStream(filter:filter,configuration:configuration,delegate:nil)
  let output=SCRecordingOutputConfiguration();output.outputURL=URL(fileURLWithPath:args[2]);output.videoCodecType = .h264;output.outputFileType = .mp4
  let delegate=RecordingDelegate();delegate.marker=args[2]+".ready";let recording=SCRecordingOutput(configuration:output,delegate:delegate)
  try stream.addRecordingOutput(recording);try await stream.startCapture()
  for _ in 0..<Int(duration*4) {if FileManager.default.fileExists(atPath:args[2]+".stop"){break};try await Task.sleep(for:.milliseconds(250))}
  do {try await stream.stopCapture()} catch {print("Stream already ended: \(error)")}
  for _ in 0..<50 {if delegate.finished{break};try await Task.sleep(for:.milliseconds(100))}
  print("Saved \(output.outputURL.path)")
 }
}
